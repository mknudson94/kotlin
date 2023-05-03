/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.providers.topics

import com.intellij.util.messages.Topic
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus

/**
 * [Topic]s for Analysis API event subscription and publication. These topics should be subscribed to and published to via the Analysis API
 * message bus: [analysisMessageBus].
 *
 * See the individual listener interfaces for documentation about the events described by these topics:
 *  - [KotlinModuleStateModificationListener]
 *  - [KotlinModuleOutOfBlockModificationListener]
 *  - [KotlinGlobalOutOfBlockModificationListener]
 *  - [KotlinGlobalSourceModuleStateModificationListener]
 *  - [KotlinGlobalSourceOutOfBlockModificationListener]
 *
 * Care needs to be taken with the lack of interplay between different types of topics: Publishing a global modification event, for example,
 * does not imply the corresponding module-level event. Similarly, publishing a module state modification event does not imply out-of-block
 * modification.
 *
 * #### Implementation Notes
 *
 * Analysis API implementations need to take care of publishing to these topics via the [analysisMessageBus]. In general, if your tool works
 * with static code and static module structure, you do not need to publish any events. However, keep in mind the contracts of the various
 * topics. For example, if your tool can guarantee a static module structure but source code can still change, module state modification
 * events do not need to be published, but out-of-block modification events do.
 */
object KotlinTopics {
    val MODULE_STATE_MODIFICATION =
        Topic(KotlinModuleStateModificationListener::class.java, Topic.BroadcastDirection.TO_CHILDREN, true)

    val MODULE_OUT_OF_BLOCK_MODIFICATION =
        Topic(KotlinModuleOutOfBlockModificationListener::class.java, Topic.BroadcastDirection.TO_CHILDREN, true)

    val GLOBAL_OUT_OF_BLOCK_MODIFICATION =
        Topic(KotlinGlobalOutOfBlockModificationListener::class.java, Topic.BroadcastDirection.TO_CHILDREN, true)

    val GLOBAL_SOURCE_MODULE_STATE_MODIFICATION =
        Topic(KotlinGlobalSourceModuleStateModificationListener::class.java, Topic.BroadcastDirection.TO_CHILDREN, true)

    val GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION =
        Topic(KotlinGlobalSourceOutOfBlockModificationListener::class.java, Topic.BroadcastDirection.TO_CHILDREN, true)
}
