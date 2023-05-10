/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.statistics

import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.statistics.metrics.StringMetrics
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test

class KotlinBuildStatHandlerTest {

    @DisplayName("Test checks that all KonanTarget names are presented in MPP_PLATFORMS statistic's report validator")
    @Test
    fun mppPlatformsShouldContainsAllKonanTargetsTest() {
        val regex = Regex(StringMetrics.MPP_PLATFORMS.anonymization.validationRegexp())

        val konanTargetsMissedInMppPlatforms = KonanTarget::class.sealedSubclasses
            .mapNotNull { sealedClass -> sealedClass.objectInstance }
            .filter { sealedClass -> !sealedClass.name.matches(regex) }

        assert(konanTargetsMissedInMppPlatforms.isEmpty()) {
            "There are platforms $konanTargetsMissedInMppPlatforms which are not presented in MPP_PLATFORMS regex"
        }
    }
}