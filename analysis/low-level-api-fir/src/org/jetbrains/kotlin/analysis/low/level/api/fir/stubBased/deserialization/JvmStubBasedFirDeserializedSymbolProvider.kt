/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.stubBased.deserialization

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.low.level.api.fir.providers.LLFirKotlinSymbolProviderWithNameCache
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.LLFirKotlinSymbolProviderNameCache
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.createDeclarationProvider
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.deserialization.SingleModuleDataProvider
import org.jetbrains.kotlin.fir.java.deserialization.JvmClassFileBasedSymbolProvider
import org.jetbrains.kotlin.fir.java.deserialization.KotlinBuiltins
import org.jetbrains.kotlin.fir.realPsi
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProviderInternals
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment

typealias DeserializedTypeAliasPostProcessor = (FirTypeAliasSymbol) -> Unit

/**
 * [JvmStubBasedFirDeserializedSymbolProvider] works over existing stubs,
 * retrieving them by classId/callableId from [KotlinDeclarationProvider].
 *
 * It works in IDE only, in standalone mode works [JvmClassFileBasedSymbolProvider].
 *
 * Because it works over existing stubs, there is no need to keep huge protobuf in memory.
 * At the same time, there is no need to guess sources for fir elements anymore,
 * they are set during deserialization.
 *
 * Same as [JvmClassFileBasedSymbolProvider], resulting fir elements are already resolved.
 */
internal open class JvmStubBasedFirDeserializedSymbolProvider(
    session: FirSession,
    moduleDataProvider: SingleModuleDataProvider,
    private val kotlinScopeProvider: FirKotlinScopeProvider,
    project: Project,
    scope: GlobalSearchScope,
    private val initialOrigin: FirDeclarationOrigin
) : LLFirKotlinSymbolProviderWithNameCache(session) {
    private val declarationProvider by lazy(LazyThreadSafetyMode.PUBLICATION) { project.createDeclarationProvider(scope) }
    private val moduleData = moduleDataProvider.getModuleData(null)

    override val symbolNameCache: LLFirKotlinSymbolProviderNameCache = LLFirKotlinSymbolProviderNameCache(session, declarationProvider)

    private val typeAliasCache: FirCache<ClassId, FirTypeAliasSymbol?, StubBasedFirDeserializationContext?> =
        session.firCachesFactory.createCacheWithPostCompute(
            createValue = { classId, context -> findAndDeserializeTypeAlias(classId, context) },
            postCompute = { _, symbol, postProcessor ->
                if (postProcessor != null && symbol != null) {
                    postProcessor.invoke(symbol)
                }
            }
        )

    private val classCache: FirCache<ClassId, FirRegularClassSymbol?, StubBasedFirDeserializationContext?> =
        session.firCachesFactory.createCache(
            createValue = { classId, context -> findAndDeserializeClass(classId, context) }
        )

    private val functionCache = session.firCachesFactory.createCache(::loadFunctionsByCallableId)
    private val propertyCache = session.firCachesFactory.createCache(::loadPropertiesByCallableId)

    private fun findAndDeserializeTypeAlias(
        classId: ClassId,
        context: StubBasedFirDeserializationContext?,
    ): Pair<FirTypeAliasSymbol?, DeserializedTypeAliasPostProcessor?> {
        val classLikeDeclaration =
            (context?.classLikeDeclaration ?: declarationProvider.getClassLikeDeclarationByClassId(classId))?.originalElement
        if (classLikeDeclaration is KtTypeAlias) {
            val symbol = FirTypeAliasSymbol(classId)
            val postProcessor: DeserializedTypeAliasPostProcessor = {
                val rootContext = context ?: StubBasedFirDeserializationContext.createRootContext(
                    moduleData,
                    StubBasedAnnotationDeserializer(session),
                    classId.packageFqName,
                    classId.relativeClassName,
                    classLikeDeclaration,
                    null, null, symbol, initialOrigin
                )
                rootContext.memberDeserializer.loadTypeAlias(classLikeDeclaration, symbol)
            }
            return symbol to postProcessor
        }
        return null to null
    }

    private fun findAndDeserializeClass(
        classId: ClassId,
        parentContext: StubBasedFirDeserializationContext?,
    ): FirRegularClassSymbol? {
        val (classLikeDeclaration, context) =
            if (parentContext?.classLikeDeclaration != null) {
                parentContext.classLikeDeclaration.originalElement to null
            } else {
                (declarationProvider.getClassLikeDeclarationByClassId(classId)?.originalElement ?: return null) to parentContext
            }

        val symbol = FirRegularClassSymbol(classId)
        if (classLikeDeclaration is KtClassOrObject) {
            deserializeClassToSymbol(
                classId,
                classLikeDeclaration,
                symbol,
                session,
                moduleData,
                StubBasedAnnotationDeserializer(session),
                kotlinScopeProvider,
                parentContext = context,
                containerSource = if (initialOrigin == FirDeclarationOrigin.BuiltIns) null else JvmFromStubDecompilerSource(
                    JvmClassName.byClassId(
                        classId
                    )
                ),
                deserializeNestedClass = this::getClass,
                initialOrigin = initialOrigin
            )
            return symbol
        }
        return null
    }

    private fun loadFunctionsByCallableId(
        callableId: CallableId,
        foundFunctions: Collection<KtNamedFunction>?,
    ): List<FirNamedFunctionSymbol> {
        val topLevelFunctions = foundFunctions ?: declarationProvider.getTopLevelFunctions(callableId)
        val origins = if (topLevelFunctions.size > 1) mutableSetOf<KtNamedFunction>() else null
        return topLevelFunctions
            .mapNotNull { function ->
                val original = function.originalElement as? KtNamedFunction ?: return@mapNotNull null
                if (origins != null && !origins.add(original)) return@mapNotNull null
                val file = original.containingKtFile
                val virtualFile = file.virtualFile
                if (virtualFile.extension == MetadataPackageFragment.METADATA_FILE_EXTENSION) return@mapNotNull null
                if (initialOrigin != FirDeclarationOrigin.BuiltIns && file.packageFqName.asString()
                        .replace(".", "/") + "/" + virtualFile.nameWithoutExtension in KotlinBuiltins
                ) return@mapNotNull null
                val symbol = FirNamedFunctionSymbol(callableId)
                val rootContext =
                    StubBasedFirDeserializationContext.createRootContext(session, moduleData, callableId, original, symbol, initialOrigin)
                rootContext.memberDeserializer.loadFunction(original, null, session, symbol).symbol
            }
    }

    private fun loadPropertiesByCallableId(callableId: CallableId, foundProperties: Collection<KtProperty>?): List<FirPropertySymbol> {
        val topLevelProperties = foundProperties ?: declarationProvider.getTopLevelProperties(callableId)
        val origins = if (topLevelProperties.size > 1) mutableSetOf<KtProperty>() else null
        return topLevelProperties
            .mapNotNull { property ->
                val original = property.originalElement as? KtProperty ?: return@mapNotNull null
                if (origins != null && !origins.add(original)) return@mapNotNull null
                val symbol = FirPropertySymbol(callableId)
                val rootContext =
                    StubBasedFirDeserializationContext.createRootContext(session, moduleData, callableId, original, symbol, initialOrigin)
                rootContext.memberDeserializer.loadProperty(original, null, symbol).symbol
            }
    }

    private fun getClass(
        classId: ClassId,
        parentContext: StubBasedFirDeserializationContext? = null
    ): FirRegularClassSymbol? {
        return classCache.getValue(classId, parentContext)
    }

    private fun getTypeAlias(classId: ClassId, context: StubBasedFirDeserializationContext? = null): FirTypeAliasSymbol? {
        if (!classId.relativeClassName.isOneSegmentFQN()) return null
        return typeAliasCache.getValue(classId, context)
    }

    @FirSymbolProviderInternals
    override fun getTopLevelCallableSymbolsTo(destination: MutableList<FirCallableSymbol<*>>, packageFqName: FqName, name: Name) {
        val callableId = CallableId(packageFqName, name)
        destination += functionCache.getCallablesWithoutContext(callableId)
        destination += propertyCache.getCallablesWithoutContext(callableId)
    }

    private fun <C : FirCallableSymbol<*>, CONTEXT> FirCache<CallableId, List<C>, CONTEXT?>.getCallablesWithoutContext(
        id: CallableId,
    ): List<C> {
        if (!symbolNameCache.mayHaveTopLevelCallable(id.packageName, id.callableName)) return emptyList()
        return getValue(id, null)
    }

    @FirSymbolProviderInternals
    override fun getTopLevelCallableSymbolsTo(
        destination: MutableList<FirCallableSymbol<*>>,
        callableId: CallableId,
        callables: Collection<KtCallableDeclaration>,
    ) {
        destination += functionCache.getValue(callableId, callables.filterIsInstance<KtNamedFunction>())
        destination += propertyCache.getValue(callableId, callables.filterIsInstance<KtProperty>())
    }

    @FirSymbolProviderInternals
    override fun getTopLevelFunctionSymbolsTo(destination: MutableList<FirNamedFunctionSymbol>, packageFqName: FqName, name: Name) {
        destination += functionCache.getCallablesWithoutContext(CallableId(packageFqName, name))
    }

    @FirSymbolProviderInternals
    override fun getTopLevelFunctionSymbolsTo(
        destination: MutableList<FirNamedFunctionSymbol>,
        callableId: CallableId,
        functions: Collection<KtNamedFunction>,
    ) {
        destination += functionCache.getValue(callableId, functions)
    }

    @FirSymbolProviderInternals
    override fun getTopLevelPropertySymbolsTo(destination: MutableList<FirPropertySymbol>, packageFqName: FqName, name: Name) {
        destination += propertyCache.getCallablesWithoutContext(CallableId(packageFqName, name))
    }

    @FirSymbolProviderInternals
    override fun getTopLevelPropertySymbolsTo(
        destination: MutableList<FirPropertySymbol>,
        callableId: CallableId,
        properties: Collection<KtProperty>,
    ) {
        destination += propertyCache.getValue(callableId, properties)
    }

    override fun getPackage(fqName: FqName): FqName? =
        fqName.takeIf {
            symbolNameCache.getTopLevelClassifierNamesInPackage(fqName)?.isNotEmpty() == true ||
                    symbolNameCache.getPackageNamesWithTopLevelCallables()?.contains(fqName.asString()) == true
        }

    override fun getClassLikeSymbolByClassId(classId: ClassId): FirClassLikeSymbol<*>? {
        if (!symbolNameCache.mayHaveTopLevelClassifier(classId, mayHaveFunctionClass = false)) return null
        return getClass(classId) ?: getTypeAlias(classId)
    }

    @FirSymbolProviderInternals
    override fun getClassLikeSymbolByClassId(classId: ClassId, classLikeDeclaration: KtClassLikeDeclaration): FirClassLikeSymbol<*>? {
        val annotationDeserializer = StubBasedAnnotationDeserializer(session)
        val deserializationContext = StubBasedFirDeserializationContext(
            moduleData,
            classId.packageFqName,
            classId.relativeClassName,
            StubBasedFirTypeDeserializer(
                moduleData,
                annotationDeserializer,
                parent = null,
                containingSymbol = null,
                owner = null,
                initialOrigin
            ),
            annotationDeserializer,
            containerSource = null,
            outerClassSymbol = null,
            outerTypeParameters = emptyList(),
            initialOrigin,
            classLikeDeclaration
        )
        if (classLikeDeclaration is KtClassOrObject) {
            return classCache.getValue(
                classId,
                deserializationContext
            )
        }
        return typeAliasCache.getValue(classId, deserializationContext)
    }

    fun getTopLevelCallableSymbol(
        packageFqName: FqName,
        shortName: Name,
        callableDeclaration: KtCallableDeclaration,
    ): FirCallableSymbol<*>? {
        //possible overloads spoils here
        //we can't use only this callable instead of index access to fill the cache
        //names check is redundant though as we already have existing callable in scope
        val callableId = CallableId(packageFqName, shortName)
        val callableSymbols = when (callableDeclaration) {
            is KtNamedFunction -> functionCache.getValue(callableId)
            is KtProperty -> propertyCache.getValue(callableId)
            else -> null
        }
        return callableSymbols?.singleOrNull { it.fir.realPsi == callableDeclaration }
    }
}
