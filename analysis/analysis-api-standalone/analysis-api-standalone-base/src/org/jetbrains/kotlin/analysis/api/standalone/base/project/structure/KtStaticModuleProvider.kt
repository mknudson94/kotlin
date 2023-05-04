/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.standalone.base.project.structure

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.psi.psiUtil.contains
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol

class KtStaticModuleProvider(
    private val builtinsModule: KtBuiltinsModule,
    val projectStructure: KtModuleProjectStructure,
) : KtStaticProjectStructureProvider() {
    @OptIn(KtModuleStructureInternals::class)
    override fun getKtModuleForKtElement(element: PsiElement): KtModule {
        val containingFileAsPsiFile = element.containingFile
        val containingFileAsVirtualFile = containingFileAsPsiFile.virtualFile
        if (containingFileAsVirtualFile.extension == BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION) {
            return builtinsModule
        }

        containingFileAsVirtualFile.analysisExtensionFileContextModule?.let { return it }

        return projectStructure.mainModules
            .first { module ->
                element in module.ktModule.contentScope
            }.ktModule
    }

    override val allKtModules: List<KtModule> = projectStructure.allKtModules()

    override val allSourceFiles: List<PsiFileSystemItem> = projectStructure.allSourceFiles()
}