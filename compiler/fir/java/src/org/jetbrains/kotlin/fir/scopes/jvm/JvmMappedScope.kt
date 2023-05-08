/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.jvm

import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltInsSignatures
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.dispatchReceiverClassLookupTagOrNull
import org.jetbrains.kotlin.fir.isSubstitutionOrIntersectionOverride
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutorByMap
import org.jetbrains.kotlin.fir.scopes.*
import org.jetbrains.kotlin.fir.scopes.impl.FirFakeOverrideGenerator
import org.jetbrains.kotlin.fir.scopes.impl.FirStandardOverrideChecker
import org.jetbrains.kotlin.fir.scopes.impl.buildSubstitutorForOverridesCheck
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.load.java.BuiltinSpecialProperties
import org.jetbrains.kotlin.load.java.SpecialGenericSignatures
import org.jetbrains.kotlin.load.java.getPropertyNamesCandidatesByAccessorName
import org.jetbrains.kotlin.load.kotlin.SignatureBuildingComponents
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

class JvmMappedScope(
    private val session: FirSession,
    private val firKotlinClass: FirRegularClass,
    private val firJavaClass: FirRegularClass,
    private val declaredMemberScope: FirContainingNamesAwareScope,
    private val javaMappedClassUseSiteScope: FirTypeScope,
) : FirTypeScope() {
    private val functionsCache = mutableMapOf<FirNamedFunctionSymbol, FirNamedFunctionSymbol>()

    private val constructorsCache = mutableMapOf<FirConstructorSymbol, FirConstructorSymbol>()

    private val overrideChecker = FirStandardOverrideChecker(session)

    private val substitutor = createMappingSubstitutor(firJavaClass, firKotlinClass, session)
    private val kotlinDispatchReceiverType = firKotlinClass.defaultType()

    private val declaredScopeOfMutableVersion = JavaToKotlinClassMap.readOnlyToMutable(firKotlinClass.classId)?.let {
        session.symbolProvider.getClassLikeSymbolByClassId(it) as? FirClassSymbol
    }?.let {
        session.declaredMemberScope(it)
    }

    private val isMutable = JavaToKotlinClassMap.isMutable(firKotlinClass.classId)

    override fun processFunctionsByName(name: Name, processor: (FirNamedFunctionSymbol) -> Unit) {
        val declared = mutableListOf<FirNamedFunctionSymbol>()
        declaredMemberScope.processFunctionsByName(name) { symbol ->
            declared += symbol
            processor(symbol)
        }

        val declaredSignatures: Set<String> by lazy {
            buildSet {
                declared.mapTo(this) { it.fir.computeJvmDescriptor() }
                declaredScopeOfMutableVersion?.processFunctionsByName(name) {
                    add(it.fir.computeJvmDescriptor())
                }
            }
        }

        javaMappedClassUseSiteScope.processFunctionsByName(name) processor@{ symbol ->
            if (!symbol.isDeclaredInMappedJavaClass() || !(symbol.fir.status as FirResolvedDeclarationStatus).visibility.isPublicAPI) {
                return@processor
            }

            val jvmDescriptor = symbol.fir.computeJvmDescriptor()
            if (jvmDescriptor in declaredSignatures || symbol.isMappedToSpecialBuiltIn(jvmDescriptor)) return@processor
            if (isOverrideOfKotlinDeclaredFunction(symbol) || isMutabilityViolation(symbol, jvmDescriptor)) return@processor

            val jdkMemberStatus = getJdkMethodStatus(jvmDescriptor)

            if (jdkMemberStatus == JDKMemberStatus.DROP) return@processor
            // hidden methods in final class can't be overridden or called with 'super'
            if (jdkMemberStatus == JDKMemberStatus.HIDDEN && firKotlinClass.isFinal) return@processor

            val newSymbol = getOrCreateSubstitutedCopy(symbol, jdkMemberStatus)
            processor(newSymbol)
        }
    }

    private fun isOverrideOfKotlinDeclaredFunction(symbol: FirNamedFunctionSymbol) =
        javaMappedClassUseSiteScope.anyOverriddenOf(symbol, ::isDeclaredInBuiltinClass)

    private fun isMutabilityViolation(symbol: FirNamedFunctionSymbol, jvmDescriptor: String): Boolean {
        val signature = SignatureBuildingComponents.signature(firJavaClass.classId, jvmDescriptor)
        if ((signature in JvmBuiltInsSignatures.MUTABLE_METHOD_SIGNATURES) xor isMutable) return true

        return javaMappedClassUseSiteScope.anyOverriddenOf(symbol) {
            !it.origin.fromSupertypes && it.containingClassLookupTag()?.classId?.let(JavaToKotlinClassMap::isMutable) == true
        }
    }

    private fun FirNamedFunctionSymbol.isMappedToSpecialBuiltIn(jvmDescriptor: String): Boolean {
        val fqName = firJavaClass.classId.asSingleFqName().child(name)
        if (valueParameterSymbols.isEmpty()) {
            if (valueParameterSymbols.isEmpty() && fqName in BuiltinSpecialProperties.GETTER_FQ_NAMES) return true
            if (getPropertyNamesCandidatesByAccessorName(name).any(::isTherePropertyWithNameInKotlinClass)) return true
        }

        val signature = SignatureBuildingComponents.signature(firJavaClass.classId, jvmDescriptor)
        return signature in SpecialGenericSignatures.JVM_SIGNATURES_FOR_RENAMED_BUILT_INS
    }

    private fun isTherePropertyWithNameInKotlinClass(name: Name): Boolean {
        if (name !in declaredMemberScope.getCallableNames()) return false

        return declaredMemberScope.getProperties(name).isNotEmpty()
    }

    private fun isDeclaredInBuiltinClass(it: FirNamedFunctionSymbol) =
        it.origin == FirDeclarationOrigin.BuiltIns || it.origin == FirDeclarationOrigin.Library

    private fun FirNamedFunctionSymbol.isDeclaredInMappedJavaClass(): Boolean {
        return !fir.isSubstitutionOrIntersectionOverride && fir.dispatchReceiverClassLookupTagOrNull() == firJavaClass.symbol.toLookupTag()
    }

    private fun getJdkMethodStatus(jvmDescriptor: String): JDKMemberStatus {
        val allClassIds = buildList {
            add(firJavaClass.classId)
            lookupSuperTypes(firJavaClass.symbol, lookupInterfaces = true, deep = true, session).mapTo(this) { superType ->
                val originalClassId = superType.fullyExpandedType(session).lookupTag.classId
                JavaToKotlinClassMap.mapKotlinToJava(originalClassId.asSingleFqName().toUnsafe()) ?: originalClassId
            }
        }

        for (classId in allClassIds) {
            when (SignatureBuildingComponents.signature(classId, jvmDescriptor)) {
                in JvmBuiltInsSignatures.HIDDEN_METHOD_SIGNATURES -> return JDKMemberStatus.HIDDEN
                in JvmBuiltInsSignatures.VISIBLE_METHOD_SIGNATURES -> return JDKMemberStatus.VISIBLE
                in JvmBuiltInsSignatures.DROP_LIST_METHOD_SIGNATURES -> return JDKMemberStatus.DROP
            }
        }

        // For unknown methods, we use HIDDEN policy by default
        return JDKMemberStatus.HIDDEN
    }

    private enum class JDKMemberStatus {
        HIDDEN, VISIBLE, DROP
    }

    override fun processPropertiesByName(name: Name, processor: (FirVariableSymbol<*>) -> Unit) {
        declaredMemberScope.processPropertiesByName(name, processor)
    }

    private fun getOrCreateSubstitutedCopy(symbol: FirNamedFunctionSymbol, jdkMemberStatus: JDKMemberStatus): FirNamedFunctionSymbol {
        return functionsCache.getOrPut(symbol) {
            val oldFunction = symbol.fir
            val newSymbol = FirNamedFunctionSymbol(CallableId(firKotlinClass.classId, symbol.callableId.callableName))
            FirFakeOverrideGenerator.createCopyForFirFunction(
                newSymbol,
                baseFunction = symbol.fir,
                derivedClassLookupTag = firKotlinClass.symbol.toLookupTag(),
                session,
                symbol.fir.origin,
                newDispatchReceiverType = kotlinDispatchReceiverType,
                newParameterTypes = oldFunction.valueParameters.map { substitutor.substituteOrSelf(it.returnTypeRef.coneType) },
                newReturnType = substitutor.substituteOrSelf(oldFunction.returnTypeRef.coneType),
            ).apply {
                if (jdkMemberStatus == JDKMemberStatus.HIDDEN) {
                    isHiddenEverywhereBesideSuperCalls = true
                }
            }
            newSymbol
        }
    }

    override fun processDirectOverriddenFunctionsWithBaseScope(
        functionSymbol: FirNamedFunctionSymbol,
        processor: (FirNamedFunctionSymbol, FirTypeScope) -> ProcessorAction
    ) = ProcessorAction.NONE

    private val firKotlinClassConstructors by lazy(LazyThreadSafetyMode.PUBLICATION) {
        firKotlinClass.constructors(session)
    }

    override fun processDeclaredConstructors(processor: (FirConstructorSymbol) -> Unit) {
        javaMappedClassUseSiteScope.processDeclaredConstructors processor@{ javaCtorSymbol ->

            fun FirConstructor.isShadowedBy(ctorFromKotlin: FirConstructorSymbol): Boolean {
                // assuming already checked for visibility
                val valueParams = valueParameters
                val valueParamsFromKotlin = ctorFromKotlin.fir.valueParameters
                if (valueParams.size != valueParamsFromKotlin.size) return false
                val substitutor = buildSubstitutorForOverridesCheck(ctorFromKotlin.fir, this@isShadowedBy, session) ?: return false
                return valueParamsFromKotlin.zip(valueParams).all { (kotlinCtorParam, javaCtorParam) ->
                    overrideChecker.isEqualTypes(kotlinCtorParam.returnTypeRef, javaCtorParam.returnTypeRef, substitutor)
                }
            }

            fun FirConstructor.isTrivialCopyConstructor(): Boolean =
                valueParameters.singleOrNull()?.let {
                    (it.returnTypeRef.coneType.lowerBoundIfFlexible() as? ConeClassLikeType)?.lookupTag == firKotlinClass.symbol.toLookupTag()
                } ?: false

            // In K1 it is handled by JvmBuiltInsCustomizer.getConstructors
            // Here the logic is generally the same, but simplified for performance by reordering checks and avoiding checking
            // for the impossible combinations
            val javaCtor = javaCtorSymbol.fir

            if (!javaCtor.status.visibility.isPublicAPI || javaCtor.isDeprecated()) return@processor
            val signature = SignatureBuildingComponents.signature(firJavaClass.classId, javaCtor.computeJvmDescriptor())
            if (signature in JvmBuiltInsSignatures.HIDDEN_CONSTRUCTOR_SIGNATURES) return@processor
            if (javaCtor.isTrivialCopyConstructor()) return@processor
            if (firKotlinClassConstructors.any { javaCtor.isShadowedBy(it) }) return@processor

            val newSymbol = getOrCreateCopy(javaCtorSymbol)
            processor(newSymbol)
        }

        declaredMemberScope.processDeclaredConstructors(processor)
    }

    private fun FirDeclaration.isDeprecated(): Boolean = symbol.getDeprecation(session, callSite = null) != null

    private fun getOrCreateCopy(symbol: FirConstructorSymbol): FirConstructorSymbol {
        return constructorsCache.getOrPut(symbol) {
            val oldConstructor = symbol.fir
            val classId = firKotlinClass.classId
            val newSymbol = FirConstructorSymbol(CallableId(classId, classId.shortClassName))
            FirFakeOverrideGenerator.createCopyForFirConstructor(
                newSymbol,
                session,
                oldConstructor,
                derivedClassLookupTag = firKotlinClass.symbol.toLookupTag(),
                symbol.fir.origin,
                newDispatchReceiverType = null,
                newReturnType = substitutor.substituteOrSelf(oldConstructor.returnTypeRef.coneType),
                newParameterTypes = oldConstructor.valueParameters.map { substitutor.substituteOrSelf(it.returnTypeRef.coneType) },
                newTypeParameters = null,
                newContextReceiverTypes = emptyList(),
                isExpect = false,
                fakeOverrideSubstitution = null
            )
            newSymbol
        }
    }

    override fun processDirectOverriddenPropertiesWithBaseScope(
        propertySymbol: FirPropertySymbol,
        processor: (FirPropertySymbol, FirTypeScope) -> ProcessorAction
    ): ProcessorAction = ProcessorAction.NONE

    override fun processClassifiersByNameWithSubstitution(name: Name, processor: (FirClassifierSymbol<*>, ConeSubstitutor) -> Unit) {
        declaredMemberScope.processClassifiersByNameWithSubstitution(name, processor)
    }

    override fun getCallableNames(): Set<Name> {
        // It's ok to return a super set of actually available member names
        return declaredMemberScope.getCallableNames() + javaMappedClassUseSiteScope.getCallableNames()
    }

    override fun getClassifierNames(): Set<Name> {
        return declaredMemberScope.getClassifierNames()
    }

    companion object {
        /**
         * For fromClass=A<T1, T2>, toClass=B<F1, F1> classes
         * @returns {T1  -> F1, T2 -> F2} substitution
         */
        private fun createMappingSubstitutor(fromClass: FirRegularClass, toClass: FirRegularClass, session: FirSession): ConeSubstitutor =
            ConeSubstitutorByMap(
                fromClass.typeParameters.zip(toClass.typeParameters).associate { (fromTypeParameter, toTypeParameter) ->
                    fromTypeParameter.symbol to ConeTypeParameterTypeImpl(
                        ConeTypeParameterLookupTag(toTypeParameter.symbol),
                        isNullable = false
                    )
                },
                session
            )
    }

    override fun toString(): String {
        return "JVM mapped scope for ${firKotlinClass.classId}"
    }
}
