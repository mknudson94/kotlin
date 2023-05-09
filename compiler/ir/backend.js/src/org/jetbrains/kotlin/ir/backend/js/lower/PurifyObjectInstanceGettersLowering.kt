/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.getOrPut
import org.jetbrains.kotlin.backend.common.ir.isPure
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.JsCommonBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.utils.isObjectInstanceField
import org.jetbrains.kotlin.ir.backend.js.utils.isObjectInstanceGetter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.*

class PurifyObjectInstanceGettersLowering(val context: JsCommonBackendContext) : DeclarationTransformer {
    private var IrClass.instanceField by context.mapping.objectToInstanceField

    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (
            !declaration.isObjectInstanceGetter() &&
            !declaration.isObjectInstanceField() &&
            !declaration.isObjectConstructor()
        ) return null

        return when (declaration) {
            is IrSimpleFunction -> declaration.purifyObjectGetterIfPossible()
            is IrField -> declaration.purifyObjectInstanceFieldIfPossible()
            is IrConstructor -> declaration.removeInstanceFieldInitializationIfPossible()
            else -> error("Unexpected IR type ${declaration::class.qualifiedName}")
        }
    }

    private fun IrConstructor.removeInstanceFieldInitializationIfPossible(): List<IrDeclaration>? {
        if (parentAsClass.isPureObject()) {
            (body as? IrBlockBody)?.statements?.removeIf {
                it is IrSetField && it.symbol.owner.isObjectInstanceField()
            }
        }

        return null
    }

    private fun IrSimpleFunction.purifyObjectGetterIfPossible(): List<IrDeclaration>? {
        val objectToCreate = returnType.classOrNull?.owner ?: return null

        if (objectToCreate.isPureObject()) {
            val body = (body as? IrBlockBody) ?: return null
            val instanceField = objectToCreate.instanceField ?: error("Expect the object instance field to be created")

            body.statements.clear()
            body.statements += JsIrBuilder.buildReturn(
                symbol,
                JsIrBuilder.buildGetField(instanceField.symbol),
                objectToCreate.defaultType
            )
        }

        return null
    }

    private fun IrField.purifyObjectInstanceFieldIfPossible(): List<IrDeclaration>? {
        val objectToCreate = type.classOrNull?.owner ?: return null

        if (objectToCreate.isPureObject()) {
            val objectConstructor = objectToCreate.primaryConstructor ?: error("Object should contain a primary constructor")
            initializer = IrExpressionBodyImpl(JsIrBuilder.buildConstructorCall(objectConstructor.symbol))
        }

        return null
    }

    private fun IrDeclaration.isObjectConstructor(): Boolean {
        return this is IrConstructor && parentAsClass.isObject
    }

    private fun IrClass.isPureObject(): Boolean {
        return context.mapping.objectsWithPureInitialization.getOrPut(this) {
            superClass == null && primaryConstructor?.body?.statements?.all { it.isPureStatementForObjectInitialization(this) } != false
        }
    }

    private fun IrStatement.isPureStatementForObjectInitialization(owner: IrClass): Boolean {
        return (this is IrDelegatingConstructorCall && symbol.owner.parent == context.irBuiltIns.anyClass.owner) ||
                (this is IrExpression && isPure(anyVariable = true, context = context)) ||
                (this is IrComposite && statements.all { it.isPureStatementForObjectInitialization(owner) }) ||
                (this is IrVariable && initializer?.isPureStatementForObjectInitialization(owner) != false) ||
                (this is IrSetField && symbol == owner.thisReceiver?.symbol && value.isPureStatementForObjectInitialization(owner)) ||
                (this is IrSetField && symbol.owner.isObjectInstanceField()) ||
                (this is IrSetValue && symbol.owner.isLocal && value.isPureStatementForObjectInitialization(owner)) ||
                (this is IrBlock && statements.all { it.isPureStatementForObjectInitialization(owner) }) ||
                (this is IrCall && symbol.owner.isObjectInstanceGetter() && symbol.owner.returnType.classOrNull?.owner?.isPureObject() == true)
    }
}
