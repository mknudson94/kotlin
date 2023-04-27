/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.native.checkers

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.classKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.native.FirNativeErrors
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.utils.superConeTypes
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.*

object FirNativeHiddenFromObjCInheritanceChecker : FirRegularClassChecker() {
    override fun check(declaration: FirRegularClass, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration.classKind == ClassKind.ENUM_ENTRY) {
            return
        }
        val session = context.session
        if (checkIsHiddenFromObjC(declaration.symbol, session)) {
            return
        }

        val superClass = declaration.superConeTypes
            .filterNot { it.isAny || it.isNullableAny }
            .find { it.toSymbol(session)?.classKind == ClassKind.CLASS }
            ?.toSymbol(session)
        superClass?.let {
            if (checkIsHiddenFromObjC(superClass, session)) {
                val source = declaration.superTypeRefs.getOrNull(0)?.source ?: declaration.source
                reporter.reportOn(source, FirNativeErrors.INHERITS_FROM_HIDDEN_FROM_OBJC_CLASS, context)
            }
        }

        val superInterfaces = declaration.superConeTypes
            .filterNot { it.isAny || it.isNullableAny }
            .filter { it.toSymbol(session)?.classKind == ClassKind.INTERFACE }
        superInterfaces.withIndex()
            .mapNotNull { (idx, type) -> type.toSymbol(session)?.let { idx to it } }
            .forEach { (idx, superInterface) ->
                if (checkIsHiddenFromObjC(superInterface, session)) {
                    val source = declaration.superTypeRefs.getOrNull(idx + 1)?.source ?: declaration.source
                    reporter.reportOn(source, FirNativeErrors.IMPLEMENTS_HIDDEN_FROM_OBJC_INTERFACE, context)
                }
            }
    }
}

private fun checkContainingClassIsHidden(classSymbol: FirClassLikeSymbol<*>, session: FirSession): Boolean {
    return classSymbol.getContainingClassSymbol(session)?.let {
        if (checkIsHiddenFromObjC(it, session)) {
            true
        } else {
            checkContainingClassIsHidden(it, session)
        }
    } ?: false
}

private fun checkIsHiddenFromObjC(classSymbol: FirClassLikeSymbol<*>, session: FirSession): Boolean {
    classSymbol.annotations.forEach { annotation ->
        val annotationClass = annotation.toAnnotationClassLikeSymbol(session) ?: return@forEach
        val objCExportMetaAnnotations = annotationClass.annotations.findMetaAnnotations(session)
        if (objCExportMetaAnnotations.hidesFromObjCAnnotation != null) {
            return true
        }
    }
    return checkContainingClassIsHidden(classSymbol, session)
}