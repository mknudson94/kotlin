/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics

/**
 * [KotlinGlobalModificationService] is a central service for the invalidation of caches during/between tests.
 *
 * Implementations of this service should publish global modification events to at least the following components:
 * - [KotlinModificationTrackerFactory]
 * - [KotlinTopics] via [analysisMessageBus]
 */
public abstract class KotlinGlobalModificationService {
    /**
     * Publishes an event of global out-of-block modification of all `KtModule`s, including binary modules. The event does not invalidate
     * module state like [publishGlobalSourceModuleStateModification], so some module structure-specific caches might persist.
     */
    @TestOnly
    public abstract fun publishGlobalOutOfBlockModification()

    /**
     * Publishes an event of global modification of the module state of all source `KtModule`s.
     */
    @TestOnly
    public abstract fun publishGlobalSourceModuleStateModification()

    /**
     * Publishes an event of global out-of-block modification of all source `KtModule`s. The event does not invalidate module state like
     * [publishGlobalSourceModuleStateModification], so some module structure-specific caches might persist.
     */
    @TestOnly
    public abstract fun publishGlobalSourceOutOfBlockModification()

    public companion object {
        public fun getInstance(project: Project): KotlinGlobalModificationService =
            project.getService(KotlinGlobalModificationService::class.java)
    }
}
