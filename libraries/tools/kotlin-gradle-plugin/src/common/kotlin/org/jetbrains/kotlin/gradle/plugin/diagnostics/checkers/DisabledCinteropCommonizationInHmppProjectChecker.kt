/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.diagnostics.checkers

import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinGradleProjectChecker
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinGradleProjectCheckerContext
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnostics
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnosticsCollector
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.targets.native.internal.CInteropCommonizerDependent
import org.jetbrains.kotlin.gradle.targets.native.internal.from
import org.jetbrains.kotlin.gradle.targets.native.internal.isAllowCommonizer
import org.jetbrains.kotlin.gradle.utils.future

internal object DisabledCinteropCommonizationInHmppProjectChecker : KotlinGradleProjectChecker {
    override fun KotlinGradleProjectCheckerContext.runChecks(collector: KotlinToolingDiagnosticsCollector) {
        if (multiplatformExtension == null
            || !project.isAllowCommonizer()
            || kotlinPropertiesProvider.enableCInteropCommonization
            || kotlinPropertiesProvider.ignoreDisabledCInteropCommonization
        ) return

        val sharedCompilationsWithInterops = multiplatformExtension.targets.flatMap { it.compilations }
            .filterIsInstance<KotlinSharedNativeCompilation>()
            .mapNotNull { compilation ->
                val cinteropDependent = project.future { CInteropCommonizerDependent.from(compilation) }
                    .getOrThrow() ?: return@mapNotNull null
                compilation to cinteropDependent
            }
            .toMap()

        val affectedCompilations = sharedCompilationsWithInterops.keys
        val affectedCInterops = sharedCompilationsWithInterops.values.flatMap { it.interops }.toSet()

        /* CInterop commonizer would not affect the project: No compilation that would actually benefit */
        if (affectedCompilations.isEmpty()) return
        if (affectedCInterops.isEmpty()) return

        val affectedSourceSetsString = affectedCompilations.map { it.defaultSourceSet.name }.sorted().joinToString(", ", "[", "]")
        val affectedCinteropsString = affectedCInterops.map { it.toString() }.sorted().joinToString(", ", "[", "]")

        collector.reportOncePerGradleProject(
            project,
            KotlinToolingDiagnostics.DisabledCinteropsCommonizationInHmppProject(affectedSourceSetsString, affectedCinteropsString)
        )
    }
}
