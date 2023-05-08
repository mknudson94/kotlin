/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter.checker

import org.jetbrains.kotlin.constant.EvaluatedConstTracker
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrStringConcatenationImpl
import org.jetbrains.kotlin.ir.interpreter.IrInterpreter
import kotlin.math.max
import kotlin.math.min

internal class IrConstExpressionTransformer(
    interpreter: IrInterpreter,
    irFile: IrFile,
    mode: EvaluationMode,
    evaluatedConstTracker: EvaluatedConstTracker?,
    onWarning: (IrFile, IrElement, IrErrorExpression) -> Unit,
    onError: (IrFile, IrElement, IrErrorExpression) -> Unit,
    suppressExceptions: Boolean,
) : IrConstTransformer(interpreter, irFile, mode, evaluatedConstTracker, onWarning, onError, suppressExceptions) {
    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.canBeInterpreted()) {
            return expression.interpret(failAsError = false)
        }
        return super.visitCall(expression)
    }

    override fun visitField(declaration: IrField): IrStatement {
        val initializer = declaration.initializer
        val expression = initializer?.expression ?: return declaration
        val isConst = declaration.correspondingPropertySymbol?.owner?.isConst == true
        if (!isConst) return super.visitField(declaration)

        if (expression.canBeInterpreted(declaration, interpreter.environment.configuration.copy(treatFloatInSpecialWay = false))) {
            initializer.expression = expression.interpret(failAsError = true)
        }

        return super.visitField(declaration)
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        fun IrExpression.wrapInStringConcat(): IrExpression = IrStringConcatenationImpl(
            this.startOffset, this.endOffset, expression.type, listOf(this@wrapInStringConcat)
        )

        fun IrExpression.wrapInToStringConcatAndInterpret(): IrExpression = wrapInStringConcat().interpret(failAsError = false)

        // here `StringBuilder`'s list is used to optimize memory, everything works without it
        val folded = mutableListOf<IrExpression>()
        val buildersList = mutableListOf<StringBuilder>()
        for (next in expression.arguments) {
            val last = folded.lastOrNull()
            when {
                !next.wrapInStringConcat().canBeInterpreted() -> {
                    folded += next
                    buildersList.add(StringBuilder())
                }
                last == null || !last.wrapInStringConcat().canBeInterpreted() -> {
                    val result = next.wrapInToStringConcatAndInterpret()
                    folded += result
                    buildersList.add(StringBuilder((result as? IrConst<*>)?.value?.toString() ?: ""))
                }
                else -> {
                    val nextAsConst = next.wrapInToStringConcatAndInterpret()
                    if (nextAsConst !is IrConst<*>) {
                        folded += next
                        buildersList.add(StringBuilder())
                    } else {
                        folded[folded.size - 1] = IrConstImpl.string(
                            // Inlined strings may have `last.startOffset > next.endOffset`
                            min(last.startOffset, next.startOffset), max(last.endOffset, next.endOffset), expression.type, ""
                        )
                        buildersList.last().append(nextAsConst.value.toString())
                    }
                }
            }
        }

        val foldedConst = folded.singleOrNull() as? IrConst<*>
        if (foldedConst != null) {
            return IrConstImpl.string(expression.startOffset, expression.endOffset, expression.type, buildersList.single().toString())
        }

        folded.zip(buildersList).forEach {
            @Suppress("UNCHECKED_CAST")
            (it.first as? IrConst<String>)?.value = it.second.toString()
        }
        return IrStringConcatenationImpl(expression.startOffset, expression.endOffset, expression.type, folded)
    }
}
