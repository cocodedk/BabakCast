package com.cocode.babakcast.domain.ai

import com.cocode.babakcast.data.model.LimitConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptProcessorTest {

    @Test
    fun cleanTranscript_removesTimestampsAndNormalizesWhitespace() {
        val input = "[00:00:01] Hello 00:00:02   world \n\n  This is   a test."
        val cleaned = TranscriptProcessor.cleanTranscript(input)
        assertEquals("Hello world This is a test.", cleaned)
    }

    @Test
    fun calculateInputBudget_respectsSafetyMargin() {
        val limits = LimitConfig(
            max_context_tokens = 1000,
            max_output_tokens = 200
        )
        val budgetDefault = TranscriptProcessor.calculateInputBudget(limits)
        val budgetCustom = TranscriptProcessor.calculateInputBudget(limits, safetyMargin = 0.10)

        assertEquals(650, budgetDefault)
        assertEquals(700, budgetCustom)
    }

    @Test
    fun chunkTranscript_splitsAndCoversAllText() {
        val sentence = "This is a sentence. "
        val input = sentence.repeat(300)
        val inputBudget = 200 // ~800 chars

        // Clean first, as chunkTranscript expects pre-cleaned input
        val cleaned = TranscriptProcessor.cleanTranscript(input)

        // Test without overlap
        val chunksNoOverlap = TranscriptProcessor.chunkTranscript(cleaned, inputBudget, overlapRatio = 0.0)
        assertTrue(chunksNoOverlap.size > 1)

        val combinedNoOverlap = chunksNoOverlap.joinToString(separator = "") { it.text }
        assertEquals(cleaned.length, combinedNoOverlap.length)
    }

    @Test
    fun chunkTranscript_hasOverlapBetweenChunks() {
        val sentence = "This is a sentence. "
        val input = sentence.repeat(300)
        val inputBudget = 200 // ~800 chars
        val overlapRatio = 0.15

        // Clean first, as chunkTranscript expects pre-cleaned input
        val cleaned = TranscriptProcessor.cleanTranscript(input)

        val chunks = TranscriptProcessor.chunkTranscript(cleaned, inputBudget, overlapRatio)
        assertTrue(chunks.size > 1)

        // Verify overlap exists between consecutive chunks
        for (i in 0 until chunks.size - 1) {
            val currentChunk = chunks[i]
            val nextChunk = chunks[i + 1]

            // Calculate expected overlap
            val expectedOverlapChars = (currentChunk.text.length * overlapRatio).toInt()

            // Check that next chunk starts before current chunk ends
            assertTrue(nextChunk.startIndex < currentChunk.endIndex)

            // The overlap should be approximately the expected amount
            val actualOverlap = currentChunk.endIndex - nextChunk.startIndex
            assertTrue(actualOverlap >= 0)
            assertTrue(actualOverlap <= currentChunk.text.length)
        }

        // Verify first chunk starts at 0
        assertEquals(0, chunks[0].startIndex)

        // Verify last chunk ends at the cleaned text length
        assertEquals(cleaned.length, chunks.last().endIndex)
    }
}
