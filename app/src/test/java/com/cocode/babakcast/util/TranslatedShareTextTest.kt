package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatedShareTextTest {

    @Test
    fun combinePutsOriginalThenSeparatorThenRtlMarkThenTranslation() {
        val result = TranslatedShareText.combine("Hello world", "سلام دنیا")
        assertEquals("Hello world\n\n———\n\n‏سلام دنیا", result)
    }

    @Test
    fun combineKeepsMultilineOriginalIntact() {
        val original = "line one\nline two"
        val result = TranslatedShareText.combine(original, "ترجمه")
        assertTrue(result.startsWith("line one\nline two\n\n———\n\n"))
    }

    @Test
    fun combineInsertsRtlMarkExactlyOnceBeforeTranslation() {
        val result = TranslatedShareText.combine("a", "ب")
        assertEquals(1, result.count { it == '‏' })
        assertEquals("‏ب", result.substringAfterLast("\n"))
    }
}
