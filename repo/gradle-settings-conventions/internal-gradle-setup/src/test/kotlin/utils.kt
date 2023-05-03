/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build

import kotlin.test.assertEquals

internal fun assertContainsExactTimes(content: String, substring: String, expectedCount: Int) {
    var currentOffset = 0
    var count = 0
    var nextIndex = content.indexOf(substring, currentOffset)

    while (nextIndex != -1 && count < expectedCount + 1) {
        count++
        currentOffset = nextIndex + substring.length
        nextIndex = content.indexOf(substring, currentOffset)
    }
    assertEquals(expectedCount, count)
}