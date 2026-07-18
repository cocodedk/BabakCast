package com.cocode.babakcast.data.ai

import com.cocode.babakcast.util.TranslatedShareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTranslatorTest {

    @Test
    fun disabledToggleSkipsWithoutResolvingOrTranslating() = runBlocking {
        var resolved = false
        val result = ShareTranslator.run(
            text = "hello",
            enabled = false,
            resolveProviderId = { resolved = true; "p1" },
            translate = { Result.success("سلام") },
            timeoutMs = 1_000
        )
        assertEquals(ShareTranslationResult.Skipped, result)
        assertTrue(!resolved)
    }

    @Test
    fun noConfiguredProviderFailsWithOriginalText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { null },
            translate = { Result.success("unused") },
            timeoutMs = 1_000
        )
        assertEquals(ShareTranslationResult.Failed("hello"), result)
    }

    @Test
    fun successReturnsCombinedText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { "p1" },
            translate = { Result.success("سلام") },
            timeoutMs = 1_000
        )
        assertEquals(
            ShareTranslationResult.Translated(TranslatedShareText.combine("hello", "سلام")),
            result
        )
    }

    @Test
    fun aiErrorFailsWithOriginalText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { "p1" },
            translate = { Result.failure(RuntimeException("boom")) },
            timeoutMs = 1_000
        )
        assertEquals(ShareTranslationResult.Failed("hello"), result)
    }

    @Test
    fun timeoutFailsWithOriginalText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { "p1" },
            translate = { delay(500); Result.success("late") },
            timeoutMs = 50
        )
        assertEquals(ShareTranslationResult.Failed("hello"), result)
    }

    @Test
    fun blankSuccessfulTranslationFailsWithOriginalText() = runBlocking {
        val result = ShareTranslator.run(
            text = "hello",
            enabled = true,
            resolveProviderId = { "p1" },
            translate = { Result.success("   ") },
            timeoutMs = 1_000
        )
        assertEquals(ShareTranslationResult.Failed("hello"), result)
    }
}
