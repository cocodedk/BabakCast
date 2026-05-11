package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextChunkerTest {

    @Test
    fun emptyText_returnsEmptyList() {
        assertEquals(emptyList<String>(), ShareTextChunker.splitForShare(""))
    }

    @Test
    fun shortText_returnsSingleChunkWithoutPrefix() {
        val text = "Hello world"
        val chunks = ShareTextChunker.splitForShare(text, maxChunkChars = 1500)
        assertEquals(listOf("Hello world"), chunks)
    }

    @Test
    fun textAtBoundary_returnsSingleChunk() {
        val text = "a".repeat(1500)
        val chunks = ShareTextChunker.splitForShare(text, maxChunkChars = 1500)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks.single())
    }

    @Test
    fun longText_splitsIntoMultipleChunksWithIndexPrefix() {
        val text = (1..40).joinToString(separator = " ") { "word$it".padEnd(20, '-') }
        val chunks = ShareTextChunker.splitForShare(text, maxChunkChars = 100)
        assertTrue("expected multiple chunks, got ${chunks.size}", chunks.size > 1)
        chunks.forEachIndexed { i, chunk ->
            assertTrue(
                "chunk $i should start with [${i + 1}/${chunks.size}] but was: ${chunk.take(20)}",
                chunk.startsWith("[${i + 1}/${chunks.size}]")
            )
        }
    }

    @Test
    fun longText_eachChunkStaysWithinMaxChars() {
        val text = (1..40).joinToString(separator = " ") { "word$it".padEnd(20, '-') }
        val max = 100
        val chunks = ShareTextChunker.splitForShare(text, maxChunkChars = max)
        chunks.forEachIndexed { i, chunk ->
            assertTrue(
                "chunk $i length ${chunk.length} exceeds max $max: $chunk",
                chunk.length <= max
            )
        }
    }

    @Test
    fun splitsAtParagraphBoundary_whenAvailable() {
        val para = "a".repeat(60)
        val text = "$para\n\n$para\n\n$para"
        val chunks = ShareTextChunker.splitForShare(text, maxChunkChars = 100)
        assertTrue("expected multiple chunks", chunks.size > 1)
        chunks.drop(1).forEachIndexed { i, chunk ->
            val body = chunk.substringAfter("] ")
            assertTrue(
                "chunk ${i + 2} should start at a paragraph boundary, got: ${body.take(20)}",
                body.startsWith("a")
            )
        }
    }

    @Test
    fun reassembling_chunkBodies_yieldsOriginalContent() {
        val text = (1..30).joinToString(separator = "\n") { "Bullet point $it with extra padding text for length" }
        val chunks = ShareTextChunker.splitForShare(text, maxChunkChars = 200)
        val rebuilt = chunks.joinToString(separator = "\n") { it.substringAfter("] ") }
        assertEquals(text.replace(Regex("\\s+"), " "), rebuilt.replace(Regex("\\s+"), " "))
    }
}
