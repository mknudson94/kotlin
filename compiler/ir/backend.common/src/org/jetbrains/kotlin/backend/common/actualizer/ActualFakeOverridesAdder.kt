/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.actualizer

import org.jetbrains.kotlin.KtDiagnosticReporterWithImplicitIrBasedContext
import org.jetbrains.kotlin.backend.common.CommonBackendErrors
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName

/**
 * It collects missing actual members to classes from common modules from actualized expect classes
 */
internal class ActualFakeOverridesAdder(
    private val expectActualMap: Map<IrSymbol, IrSymbol>,
    private val typeAliasMap: Map<FqName, FqName>,
    private val diagnosticsReporter: KtDiagnosticReporterWithImplicitIrBasedContext
) : IrElementVisitorVoid {
    private val missingActualMembersMap = mutableMapOf<IrClass, MutableMap<String, MutableList<IrDeclaration>>>()

    override fun visitClass(declaration: IrClass) {
        extractMissingActualMembersFromSupertypes(declaration)
        visitElement(declaration)
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    private fun extractMissingActualMembersFromSupertypes(irClass: IrClass): Map<String, MutableList<IrDeclaration>> {
        missingActualMembersMap[irClass]?.let { return it }

        val missingActualMembers = mutableMapOf<String, MutableList<IrDeclaration>>()
        missingActualMembersMap[irClass] = missingActualMembers

        val actualClass = expectActualMap[irClass.symbol]?.owner as? IrClass

        // New members from supertypes are only relevant for not expect (ordinary) classes
        // New members from the current class are only relevant for actualized expect classes
        // actualClass == null means that the current class is not expect

        for (superType in irClass.superTypes) {
            val membersFromSupertype = extractMissingActualMembersFromSupertypes(superType.classifierOrFail.owner as IrClass)
            if (actualClass == null) {
                for (memberFromSupertype in membersFromSupertype.flatMap { it.value }) {
                    val newMember = createFakeOverrideMember(listOf(memberFromSupertype), irClass)
                    val mainSignature = generateIrElementFullNameFromExpect(newMember, typeAliasMap)
                    if (missingActualMembers.getMatches(mainSignature, newMember, expectActualMap).isEmpty()) {
                        missingActualMembers[mainSignature] = mutableListOf(newMember)
                        irClass.addMember(newMember)
                    } else {
                        diagnosticsReporter.at(irClass).report(
                            CommonBackendErrors.MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED,
                            irClass.name.asString(),
                            (memberFromSupertype as IrDeclarationWithName).name.asString()
                        )
                    }
                }
            }
        }

        if (actualClass == null) {
            return missingActualMembers
        }

        val actualWithCorrespondingExpectMembers = mutableSetOf<IrSymbol>().apply {
            irClass.declarations.mapNotNullTo(this) { expectActualMap[it.symbol] }
        }

        // Searching for missing members for actualized expect classes
        for (actualMember in actualClass.declarations) {
            if ((actualMember is IrSimpleFunction || actualMember is IrProperty) &&
                !actualWithCorrespondingExpectMembers.contains(actualMember.symbol)
            ) {
                missingActualMembers.getOrPut(generateIrElementFullNameFromExpect(actualMember, typeAliasMap)) { mutableListOf() }.add(actualMember)
            }
        }

        return missingActualMembers
    }
}