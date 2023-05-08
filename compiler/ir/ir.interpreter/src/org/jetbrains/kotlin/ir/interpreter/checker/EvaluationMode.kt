/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter.checker

import org.jetbrains.kotlin.ir.BuiltInOperatorNames
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.interpreter.*
import org.jetbrains.kotlin.ir.interpreter.hasAnnotation
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isUnsignedType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

enum class EvaluationMode {
    FULL {
        override fun canEvaluateFunction(function: IrFunction, context: IrCall?): Boolean = true
        override fun canEvaluateEnumValue(enumEntry: IrGetEnumValue, context: IrCall?): Boolean = true
        override fun canEvaluateFunctionExpression(expression: IrFunctionExpression, context: IrCall?): Boolean = true
        override fun canEvaluateCallableReference(reference: IrCallableReference<*>, context: IrCall?): Boolean = true
        override fun canEvaluateClassReference(reference: IrDeclarationReference): Boolean = true

        override fun canEvaluateBlock(block: IrBlock): Boolean = true
        override fun canEvaluateComposite(composite: IrComposite): Boolean = true

        override fun canEvaluateExpression(expression: IrExpression): Boolean = true

        override fun mustCheckBodyOf(function: IrFunction): Boolean = true
    },

    ONLY_BUILTINS {
        private val allowedMethodsOnPrimitives = setOf(
            "not", "unaryMinus", "unaryPlus", "inv",
            "toString", "toChar", "toByte", "toShort", "toInt", "toLong", "toFloat", "toDouble",
            "equals", "compareTo", "plus", "minus", "times", "div", "rem", "and", "or", "xor", "shl", "shr", "ushr",
            "less", "lessOrEqual", "greater", "greaterOrEqual"
        )
        private val allowedMethodsOnStrings = setOf(
            "<get-length>", "plus", "get", "compareTo", "equals", "toString"
        )
        private val allowedExtensionFunctions = setOf(
            "kotlin.floorDiv", "kotlin.mod", "kotlin.NumbersKt.floorDiv", "kotlin.NumbersKt.mod", "kotlin.<get-code>"
        )
        private val allowedBuiltinExtensionFunctions = listOf(
            BuiltInOperatorNames.LESS, BuiltInOperatorNames.LESS_OR_EQUAL,
            BuiltInOperatorNames.GREATER, BuiltInOperatorNames.GREATER_OR_EQUAL,
            BuiltInOperatorNames.EQEQ, BuiltInOperatorNames.IEEE754_EQUALS,
            BuiltInOperatorNames.ANDAND, BuiltInOperatorNames.OROR
        ).map { IrBuiltIns.KOTLIN_INTERNAL_IR_FQN.child(Name.identifier(it)).asString() }.toSet()

        override fun canEvaluateFunction(function: IrFunction, context: IrCall?): Boolean {
            if (function.property?.isConst == true) return true

            val returnType = function.returnType
            if (!returnType.isPrimitiveType() && !returnType.isString() && !returnType.isUnsignedType()) return false

            val fqName = function.fqNameWhenAvailable?.asString()
            val parent = function.parentClassOrNull
            val parentType = parent?.defaultType
            return when {
                parentType == null -> fqName in allowedExtensionFunctions || fqName in allowedBuiltinExtensionFunctions
                parentType.isPrimitiveType() -> function.name.asString() in allowedMethodsOnPrimitives
                parentType.isString() -> function.name.asString() in allowedMethodsOnStrings
                parent.isObject -> parent.parentClassOrNull?.defaultType?.let { it.isPrimitiveType() || it.isUnsigned() } == true
                parentType.isUnsignedType() && function is IrConstructor -> true
                else -> fqName in allowedExtensionFunctions || fqName in allowedBuiltinExtensionFunctions
            }
        }

        override fun canEvaluateEnumValue(enumEntry: IrGetEnumValue, context: IrCall?): Boolean = false
        override fun canEvaluateFunctionExpression(expression: IrFunctionExpression, context: IrCall?): Boolean = false
        override fun canEvaluateCallableReference(reference: IrCallableReference<*>, context: IrCall?): Boolean = false
        override fun canEvaluateClassReference(reference: IrDeclarationReference): Boolean = false
        override fun canEvaluateBlock(block: IrBlock): Boolean = block.statements.size == 1
        override fun canEvaluateExpression(expression: IrExpression): Boolean = expression is IrCall
    },

    ONLY_INTRINSIC_CONST {
        override fun canEvaluateFunction(function: IrFunction, context: IrCall?): Boolean {
            return function.isCompileTimePropertyAccessor() ||
                    function.isMarkedAsIntrinsicConstEvaluation() ||
                    context.isIntrinsicConstEvaluationNameProperty()
        }

        private fun IrFunction?.isCompileTimePropertyAccessor(): Boolean {
            val property = this?.property ?: return false
            return property.isConst || (property.resolveFakeOverride() ?: property).isMarkedAsIntrinsicConstEvaluation()
        }

        override fun canEvaluateEnumValue(enumEntry: IrGetEnumValue, context: IrCall?): Boolean {
            return context.isIntrinsicConstEvaluationNameProperty()
        }

        override fun canEvaluateCallableReference(reference: IrCallableReference<*>, context: IrCall?): Boolean {
            return context.isIntrinsicConstEvaluationNameProperty()
        }

        override fun canEvaluateFunctionExpression(expression: IrFunctionExpression, context: IrCall?): Boolean = false
        override fun canEvaluateClassReference(reference: IrDeclarationReference): Boolean = false
        override fun canEvaluateBlock(block: IrBlock): Boolean = block.origin == IrStatementOrigin.WHEN || block.statements.size == 1
        override fun canEvaluateExpression(expression: IrExpression): Boolean = expression is IrCall || expression is IrWhen

        private fun IrCall?.isIntrinsicConstEvaluationNameProperty(): Boolean {
            if (this == null) return false
            val owner = this.symbol.owner
            val property = owner.property ?: return false
            return owner.isCompileTimePropertyAccessor() && property.name.asString() == "name"
        }
    };

    open fun canEvaluateFunction(function: IrFunction, context: IrCall? = null): Boolean = false
    open fun canEvaluateEnumValue(enumEntry: IrGetEnumValue, context: IrCall? = null): Boolean = false
    open fun canEvaluateFunctionExpression(expression: IrFunctionExpression, context: IrCall? = null): Boolean = false
    open fun canEvaluateCallableReference(reference: IrCallableReference<*>, context: IrCall? = null): Boolean = false
    open fun canEvaluateClassReference(reference: IrDeclarationReference): Boolean = false

    open fun canEvaluateBlock(block: IrBlock): Boolean = false
    open fun canEvaluateComposite(composite: IrComposite): Boolean {
        return composite.origin == IrStatementOrigin.DESTRUCTURING_DECLARATION || composite.origin == null
    }

    open fun canEvaluateExpression(expression: IrExpression): Boolean = false

    open fun mustCheckBodyOf(function: IrFunction): Boolean {
        return function.property != null
    }

    protected fun IrDeclaration.isMarkedAsIntrinsicConstEvaluation() = isMarkedWith(intrinsicConstEvaluationAnnotation)

    protected fun IrDeclaration.isMarkedWith(annotation: FqName): Boolean {
        if (this is IrClass && this.isCompanion) return false
        if (this.hasAnnotation(annotation)) return true
        return (this.parent as? IrClass)?.isMarkedWith(annotation) ?: false
    }
}

private val IrFunction.property: IrProperty?
    get() = (this as? IrSimpleFunction)?.correspondingPropertySymbol?.owner
