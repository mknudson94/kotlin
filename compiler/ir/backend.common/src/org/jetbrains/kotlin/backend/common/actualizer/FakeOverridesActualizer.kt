/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.actualizer

import org.jetbrains.kotlin.backend.common.ir.isExpect
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.transformInPlace
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 *  It actualizes fake override expect members in base and inherited not expect classes
 *  It uses @property expectActualMap as cache of already actualized members
 */
internal class FakeOverridesActualizer(private val expectActualMap: MutableMap<IrSymbol, IrSymbol>) : IrElementVisitorVoid {
    override fun visitClass(declaration: IrClass) {
        if (!declaration.isExpect) {
            actualizeFakeOverrides(declaration)
        }
        visitElement(declaration)
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    private fun actualizeFakeOverrides(declaration: IrClass) {
        fun IrDeclaration.actualize(): IrDeclaration? {
            (expectActualMap[symbol]?.owner as? IrDeclaration)?.let { return it }

            require(this is IrOverridableDeclaration<*>)
            // It returns null if there is no actual member (diagnostics should be reported earlier)
            if (overriddenSymbols.isEmpty()) return null
            val actualizedOverrides = overriddenSymbols.mapNotNull { (it.owner as IrDeclaration).actualize() }
            val actualFakeOverride = createFakeOverrideMember(actualizedOverrides, parent as IrClass)

            expectActualMap.addLink(this as IrDeclarationBase, actualFakeOverride)

            return actualFakeOverride
        }

        declaration.declarations.transformInPlace { if (it.isExpect) it.actualize() ?: it else it }
    }
}