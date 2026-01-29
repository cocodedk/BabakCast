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

        val chunks = TranscriptProcessor.chunkTranscript(input, inputBudget)
        assertTrue(chunks.size > 1)

        val cleaned = TranscriptProcessor.cleanTranscript(input)
        val combined = chunks.joinToString(separator = "") { it.text }
        assertEquals(cleaned.length, combined.length)
    }
}
