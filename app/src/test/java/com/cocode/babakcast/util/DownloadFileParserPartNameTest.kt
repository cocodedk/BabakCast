package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DownloadFileParserPartNameTest {

    @Test
    fun formatPartNumber_existsAndSupportsLexicalOrdering() {
        val method = DownloadFileParser::class.java.methods.firstOrNull {
            it.name == "formatPartNumber" && it.parameterCount == 2
        }
        assertNotNull(
            "DownloadFileParser.formatPartNumber(partNumber: Int, totalPartsHint: Int) must exist",
            method
        )

        val tokens = listOf(1, 2, 9, 10, 11).map { part ->
            method!!.invoke(DownloadFileParser, part, 9) as String
        }
        val sorted = tokens.sorted()

        assertEquals(
            "Part number tokens must remain lexically sortable even when actual parts exceed estimate",
            tokens,
            sorted
        )
    }

    @Test
    fun parsesBothLegacyAndCleanSchemes() {
        assertEquals("episode_audio_dQw4w9WgXcQ", DownloadFileParser.extractGroupKey("episode_audio_dQw4w9WgXcQ_part0003"))
        assertEquals(3, DownloadFileParser.extractPartNumber("episode_audio_dQw4w9WgXcQ_part0003"))
        assertEquals("My Episode", DownloadFileParser.extractGroupKey("My Episode — Part 02 of 12"))
        assertEquals(2, DownloadFileParser.extractPartNumber("My Episode — Part 02 of 12"))
        assertEquals("Just A Title", DownloadFileParser.extractGroupKey("Just A Title"))
    }
}
