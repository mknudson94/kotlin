/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers.impl

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics

public abstract class KotlinGlobalModificationServiceBase(private val project: Project) : KotlinGlobalModificationService() {
    @TestOnly
    protected abstract fun incrementModificationTrackers(includeBinaryTrackers: Boolean)

    @TestOnly
    override fun publishGlobalOutOfBlockModification() {
        incrementModificationTrackers(includeBinaryTrackers = true)
        project.analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_OUT_OF_BLOCK_MODIFICATION).afterModification()
    }

    @TestOnly
    override fun publishGlobalSourceModuleStateModification() {
        incrementModificationTrackers(includeBinaryTrackers = false)
        project.analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_SOURCE_MODULE_STATE_MODIFICATION).afterModification()
    }

    @TestOnly
    override fun publishGlobalSourceOutOfBlockModification() {
        incrementModificationTrackers(includeBinaryTrackers = false)
        project.analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION).afterModification()
    }
}
