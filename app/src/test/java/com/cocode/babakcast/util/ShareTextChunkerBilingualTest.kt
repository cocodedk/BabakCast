package com.cocode.babakcast.util

import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextChunkerBilingualTest {

    @Test
    fun combinedOriginalPlusTranslationStillChunksWithinLimit() {
        val original = buildString {
            repeat(60) { appendLine("Sentence number $it of a long English summary.") }
        }
        val translated = buildString {
            repeat(60) { appendLine("جمله شماره $it از یک خلاصه طولانی فارسی.") }
        }
        val combined = TranslatedShareText.combine(original, translated)
        val chunks = ShareTextChunker.splitForShare(combined)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= ShareTextChunker.DEFAULT_MAX_CHUNK_CHARS })
        assertTrue(chunks.size > 1)
    }
}
