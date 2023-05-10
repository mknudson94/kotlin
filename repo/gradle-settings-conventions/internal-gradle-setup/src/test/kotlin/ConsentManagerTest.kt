/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build

import org.apache.tools.ant.filters.StringInputStream
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsentManagerTest {
    @TempDir
    lateinit var workingDir: Path

    private val localPropertiesFile by lazy {
        workingDir.resolve("local.properties")
    }

    private val modifier by lazy {
        LocalPropertiesModifier(localPropertiesFile.toFile())
    }

    @Test
    @DisplayName("can check if there's no decision over the consent")
    fun testNoConsentRead() {
        assertNull(ConsentManager(modifier).getUserDecision())
    }

    @Test
    @DisplayName("can check if there's agree to the consent")
    fun testConsentAgreeRead() {
        localPropertiesFile.toFile().writeText(USER_CONSENT_MARKER)
        assertEquals(true, ConsentManager(modifier).getUserDecision())
    }

    @Test
    @DisplayName("can check if there's refusal to the consent")
    fun testConsentRefusalRead() {
        localPropertiesFile.toFile().writeText(USER_REFUSAL_MARKER)
        assertEquals(false, ConsentManager(modifier).getUserDecision())
    }

    @Test
    @Timeout(1)
    fun testConsentRequestAgree() = testUserDecision("yes", true, USER_CONSENT_MARKER)

    @Test
    @Timeout(1)
    fun testConsentRequestRefusal() = testUserDecision("no", false, USER_REFUSAL_MARKER)

    @Test
    @Timeout(1)
    fun testConsentRequestAnswerAfterBadInput() = testUserDecision("\nasdasd\nno", false, USER_REFUSAL_MARKER, 3)

    private fun testUserDecision(prompt: String, expectedDecision: Boolean, expectedLine: String, promptCount: Int = 1) {
        StringInputStream(prompt).bufferedReader().use { input ->
            val outputStream = ByteArrayOutputStream(255)
            PrintStream(outputStream).use { printStream ->
                val consentManager = ConsentManager(modifier, input, printStream)
                val userDecision = consentManager.askForConsent()
                assertEquals(expectedDecision, userDecision)
                val content = Files.readAllLines(localPropertiesFile)
                assertTrue(content.contains(expectedLine))
                val output = String(outputStream.toByteArray())
                assertContainsExactTimes(output, USER_CONSENT_REQUEST, 1)
                assertContainsExactTimes(output, PROMPT_REQUEST, promptCount)
            }
        }
    }
}