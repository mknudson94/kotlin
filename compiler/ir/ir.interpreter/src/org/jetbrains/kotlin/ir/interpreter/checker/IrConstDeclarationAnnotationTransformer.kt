/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter.checker

import org.jetbrains.kotlin.constant.EvaluatedConstTracker
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.interpreter.IrInterpreter
import org.jetbrains.kotlin.ir.util.primaryConstructor

internal class IrConstDeclarationAnnotationTransformer(
    interpreter: IrInterpreter,
    irFile: IrFile,
    mode: EvaluationMode,
    evaluatedConstTracker: EvaluatedConstTracker?,
    onWarning: (IrFile, IrElement, IrErrorExpression) -> Unit,
    onError: (IrFile, IrElement, IrErrorExpression) -> Unit,
    suppressExceptions: Boolean,
) : IrConstAnnotationTransformer(interpreter, irFile, mode, evaluatedConstTracker, onWarning, onError, suppressExceptions) {
    override fun visitDeclaration(declaration: IrDeclarationBase, data: Nothing?): IrStatement {
        transformAnnotations(declaration)
        if (declaration is IrClass && declaration.kind == ClassKind.ANNOTATION_CLASS) {
            declaration.primaryConstructor?.valueParameters?.forEach {
                val defaultExpression = it.defaultValue?.expression ?: return@forEach
                if (defaultExpression.canBeInterpreted()) {
                    it.defaultValue?.expression = transformAnnotationArgument(defaultExpression, it)
                }
            }
        }
        return super.visitDeclaration(declaration, data)
    }
}
