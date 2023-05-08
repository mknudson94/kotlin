/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.checkUpperBoundViolated
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.createSubstitutorForUpperBoundViolationCheck
import org.jetbrains.kotlin.fir.analysis.checkers.toTypeArgumentsWithSourceInfo
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier

object FirTypeArgumentsOfResolvedQualifierChecker : FirResolvedQualifierChecker() {
    override fun check(expression: FirResolvedQualifier, context: CheckerContext, reporter: DiagnosticReporter) {
        val typeParameters = expression.symbol?.typeParameterSymbols ?: emptyList()
        val typeArguments = expression.typeArguments.toTypeArgumentsWithSourceInfo()
        if (typeArguments.size != typeParameters.size && !expression.resolvedToCompanionObject) {
            // TODO: report WRONG_NUMBER_OF_TYPE_ARGUMENTS
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
