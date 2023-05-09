/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.checkUpperBoundViolated
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.createSubstitutorForUpperBoundViolationCheck
import org.jetbrains.kotlin.fir.analysis.checkers.toTypeArgumentsWithSourceInfo
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.calls.FirSyntheticFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol

object FirTypeArgumentsOfResolvedQualifierChecker : FirResolvedQualifierChecker() {
    override fun check(expression: FirResolvedQualifier, context: CheckerContext, reporter: DiagnosticReporter) {
        val correspondingClass = expression.symbol
        val typeParameters = when {
            correspondingClass is FirTypeAliasSymbol -> {
                /*
                 * If qualified typealias stays in position of expression (expression.coneType != Unit) and this typealias
                 *   points to some object, then we should assume that it has no type parameters, even if typealias itself
                 *   has those parameters
                 *
                 * object SomeObject
                 *
                 * typealias AliasedObject<T> = SomeObject
                 *
                 * val x = AliasedObject // ok
                 */
                when {
                    expression.typeRef.coneType.isUnit -> correspondingClass.typeParameterSymbols
                    expression.typeRef.coneType.fullyExpandedType(context.session)
                        .toRegularClassSymbol(context.session)
                        ?.classKind == ClassKind.OBJECT -> emptyList()
                    else -> correspondingClass.typeParameterSymbols
                }
            }
            expression.resolvedToCompanionObject -> emptyList()
            else -> correspondingClass?.typeParameterSymbols.orEmpty()
        }
        val typeArguments = expression.typeArguments.toTypeArgumentsWithSourceInfo()
        if (typeArguments.size != typeParameters.size) {
            val emptyArgumentsAreAllowed =
                when (val containingElement = context.containingElements.lastOrNull { it != expression && it !is FirArgumentList }) {
                    is FirQualifiedAccessExpression -> run r@{
                        if (containingElement.explicitReceiver != expression) return@r false

                        /*
                         * If there is no symbol then the reference is an error reference, so we already reported some other error
                         */
                        val callableSymbol = containingElement.toResolvedCallableSymbol() ?: return@r true

                        when {
                            /*
                             * Static methods can be called without type arguments
                             *
                             * public class JavaClass<T> {
                             *     public static void foo() {}
                             * }
                             *
                             * fun test() {
                             *     JavaClass.foo()
                             * }
                             */
                            callableSymbol.isStatic -> true
                            /*
                             * Constructors of nested classes can be called without type arguments
                             *
                             * class Outer<T> {
                             *     class Nested
                             *     inner class Inner
                             * }
                             *
                             * fun test() {
                             *     val nested = Outer.Nested() // ok
                             *     val inner = Outer.Inner() // error
                             * }
                             */
                            callableSymbol is FirConstructorSymbol && !callableSymbol.isInner -> true
                            /*
                             * SAM constructors for nested interfaces can be called without type arguments
                             *
                             * class Outer<T> {
                             *     fun interface Sam {
                             *         fun foo(): String
                             *     }
                             * }
                             *
                             * fun takeSam(some: Outer.Sam) {}
                             *
                             * fun test() {
                             *     takeSam(Outer.Sam { "hello" })
                             * }
                             */
                            callableSymbol is FirSyntheticFunctionSymbol && callableSymbol.origin is FirDeclarationOrigin.SamConstructor -> true
                            else -> false
                        }
                    }
                    is FirGetClassCall -> containingElement.argument == expression
                    else -> false
                }
            val shouldReportError = typeArguments.isNotEmpty() || !emptyArgumentsAreAllowed
            if (shouldReportError) {
                val symbol = expression.symbol
                if (symbol != null) {
                    reporter.reportOn(expression.source, FirErrors.WRONG_NUMBER_OF_TYPE_ARGUMENTS, typeParameters.size, symbol, context)
                }
            }
            return
        }
        if (typeArguments.any { it.coneTypeProjection !is ConeKotlinType }) {
            return
        }
        val substitutor = createSubstitutorForUpperBoundViolationCheck(typeParameters, typeArguments, context.session)
        checkUpperBoundViolated(
            context,
            reporter,
            typeParameters,
            typeArguments,
            substitutor,
        )
    }
}
