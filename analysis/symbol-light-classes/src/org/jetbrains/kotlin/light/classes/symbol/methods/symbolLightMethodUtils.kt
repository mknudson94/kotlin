/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.methods

import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.light.classes.symbol.classes.SymbolLightClassBase
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.JVM_INLINE_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.util.OperatorNameConventions

context(KtAnalysisSession)
internal fun String.isSuppressedFinalModifier(containingClass: SymbolLightClassBase, symbol: KtCallableSymbol): Boolean {
    if (this != PsiModifier.FINAL) return false
    if (containingClass.isInterface) return true
    if (symbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED) {
        if (containingClass.isEnum) return true
        if (symbol is KtFunctionSymbol && symbol.name.isFromAny && !containingClass.hasAnnotation(JVM_INLINE_ANNOTATION_FQ_NAME.toString())) return true
    }
    return false
}

internal val Name.isFromAny: Boolean
    get() = this == OperatorNameConventions.EQUALS || this == StandardNames.HASHCODE_NAME || this == OperatorNameConventions.TO_STRING

