/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.generator.print

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.kotlin.ir.generator.IrTree
import org.jetbrains.kotlin.ir.generator.VISITOR_PACKAGE
import org.jetbrains.kotlin.ir.generator.irTypeType
import org.jetbrains.kotlin.ir.generator.model.*
import org.jetbrains.kotlin.ir.generator.util.GeneratedFile
import java.io.File

private val visitorTypeName = ClassName(VISITOR_PACKAGE, "IrElementVisitor")
private val visitorVoidTypeName = ClassName(VISITOR_PACKAGE, "IrElementVisitorVoid")
private val transformerTypeName = ClassName(VISITOR_PACKAGE, "IrElementTransformer")
private val typeTransformerVoidTypeName = ClassName(VISITOR_PACKAGE, "IrTypeTransformerVoid")

fun printVisitor(generationPath: File, model: Model): GeneratedFile {
    val visitorType = TypeSpec.interfaceBuilder(visitorTypeName).apply {
        val r = TypeVariableName("R", KModifier.OUT)
        val d = TypeVariableName("D", KModifier.IN)
        addTypeVariable(r)
        addTypeVariable(d)

        fun buildVisitFun(element: Element) = FunSpec.builder(element.visitFunName).apply {
            addParameter(element.visitorParam, element.toPoetStarParameterized())
            addParameter("data", d)
            returns(r)
        }

        addFunction(buildVisitFun(model.rootElement).addModifiers(KModifier.ABSTRACT).build())

        for (element in model.elements) {
            element.visitorParent?.let { parent ->
                addFunction(buildVisitFun(element).apply {
                    addStatement("return ${parent.element.visitFunName}(${element.visitorParam}, data)")
                }.build())
            }
        }
    }.build()

    return printTypeCommon(generationPath, visitorTypeName.packageName, visitorType)
}

fun printVisitorVoid(generationPath: File, model: Model): GeneratedFile {
    val dataType = NOTHING.copy(nullable = true)

    val visitorType = TypeSpec.interfaceBuilder(visitorVoidTypeName).apply {
        addSuperinterface(visitorTypeName.parameterizedBy(UNIT, dataType))

        fun buildVisitFun(element: Element) = FunSpec.builder(element.visitFunName).apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter(element.visitorParam, element.toPoetStarParameterized())
            addParameter("data", dataType)
            addStatement("return ${element.visitFunName}(${element.visitorParam})")
        }

        fun buildVisitVoidFun(element: Element) = FunSpec.builder(element.visitFunName).apply {
            addParameter(element.visitorParam, element.toPoetStarParameterized())
        }

        addFunction(buildVisitFun(model.rootElement).build())
        addFunction(buildVisitVoidFun(model.rootElement).build())

        for (element in model.elements) {
            element.visitorParent?.let { parent ->
                addFunction(buildVisitFun(element).build())
                addFunction(buildVisitVoidFun(element).apply {
                    addStatement("return ${parent.element.visitFunName}(${element.visitorParam})")
                }.build())
            }
        }
    }.build()

    return printTypeCommon(generationPath, visitorVoidTypeName.packageName, visitorType)
}

fun printTransformer(generationPath: File, model: Model): GeneratedFile {
    val visitorType = TypeSpec.interfaceBuilder(transformerTypeName).apply {
        val d = TypeVariableName("D", KModifier.IN)
        addTypeVariable(d)

        addSuperinterface(visitorTypeName.parameterizedBy(model.rootElement.toPoetStarParameterized(), d))

        fun buildVisitFun(element: Element) = FunSpec.builder(element.visitFunName).apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter(element.visitorParam, element.toPoetStarParameterized())
            addParameter("data", d)
        }

        for (element in model.elements) {
            if (element.transformByChildren) {
                addFunction(buildVisitFun(element).apply {
                    addStatement("${element.visitorParam}.transformChildren(this, data)")
                    addStatement("return ${element.visitorParam}")
                    returns((element.transformerReturnType ?: element).toPoetStarParameterized())
                }.build())
            } else {
                element.visitorParent?.let { parent ->
                    addFunction(buildVisitFun(element).apply {
                        addStatement("return ${parent.element.visitFunName}(${element.visitorParam}, data)")
                        element.transformerReturnType?.let {
                            returns(it.toPoetStarParameterized())
                        }
                    }.build())
                }
            }
        }
    }.build()

    return printTypeCommon(generationPath, transformerTypeName.packageName, visitorType)
}

fun printTypeVisitor(generationPath: File, model: Model): GeneratedFile {
    val transformTypeFunName = "transformType"

    fun FunSpec.Builder.addVisitTypeStatement(element: Element, field: Field) {
        val visitorParam = element.visitorParam
        val access = "$visitorParam.${field.name}"
        when (field) {
            is SingleField -> addStatement("$access = $transformTypeFunName($visitorParam, $access, data)")
            is ListField -> addStatement("$access = $access.map { $transformTypeFunName($visitorParam, it, data) }")
        }
    }

    fun Element.getFieldsWithIrTypeType(insideParent: Boolean = false): List<Field> {
        val parentsFields = elementParents.flatMap { it.element.getFieldsWithIrTypeType(insideParent = true) }
        if (insideParent && this.visitorParent != null) {
            return parentsFields
        }

        val irTypeFields = this.fields
            .filter {
                val type = when (it) {
                    is SingleField -> it.type
                    is ListField -> it.elementType
                }
                type.toString() == irTypeType.toString()
            }

        return irTypeFields + parentsFields
    }

    val visitorType = TypeSpec.interfaceBuilder(typeTransformerVoidTypeName).apply {
        val d = TypeVariableName("D", KModifier.IN)
        addTypeVariable(d)
        addSuperinterface(transformerTypeName.parameterizedBy(d))

        val abstractVisitFun = FunSpec.builder(transformTypeFunName).apply {
            val poetNullableIrType = irTypeType.toPoet().copy(nullable = true)
            val typeVariable = TypeVariableName("Type", poetNullableIrType)
            addTypeVariable(typeVariable)
            addParameter("container", model.rootElement.toPoet())
            addParameter("type", typeVariable)
            addParameter("data", d)
            returns(typeVariable)
        }
        addFunction(abstractVisitFun.addModifiers(KModifier.ABSTRACT).build())

        fun buildVisitFun(element: Element) = FunSpec.builder(element.visitFunName).apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter(element.visitorParam, element.toPoetStarParameterized())
            addParameter("data", d)
        }

        for (element in model.elements) {
            val irTypeFields = element.getFieldsWithIrTypeType()
            if (irTypeFields.isEmpty()) continue

            element.visitorParent?.let { _ ->
                addFunction(buildVisitFun(element).apply {
                    // Note: using `run` here to infer return type automatically
                    beginControlFlow("return run")

                    val visitorParam = element.visitorParam
                    when (element.name) {
                        IrTree.memberAccessExpression.name -> {
                            beginControlFlow("(0 until $visitorParam.typeArgumentsCount).forEach {")
                            beginControlFlow("$visitorParam.getTypeArgument(it)?.let { type ->")
                            addStatement("expression.putTypeArgument(it, $transformTypeFunName($visitorParam, type, data))")
                            endControlFlow()
                            endControlFlow()
                        }
                        IrTree.`class`.name -> {
                            beginControlFlow("$visitorParam.valueClassRepresentation?.mapUnderlyingType {")
                            addStatement("$transformTypeFunName($visitorParam, it, data)")
                            endControlFlow()
                            irTypeFields.forEach { addVisitTypeStatement(element, it) }
                        }
                        else -> irTypeFields.forEach { addVisitTypeStatement(element, it) }
                    }
                    addStatement("return@run super.${element.visitFunName}($visitorParam, data)")
                    endControlFlow()
                }.build())
            }
        }
    }.build()

    return printTypeCommon(generationPath, typeTransformerVoidTypeName.packageName, visitorType)
}
