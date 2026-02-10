package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeMetadataParserTest {

    @Test
    fun extractTitleFromJson_decodesUnicodeEscapes() {
        val json = "{\"title\":\"Ali\\u0027s Mix - 2026 \\u2013 Live\"}"

        val title = YouTubeMetadataParser.extractTitleFromJson(json)

        assertEquals("Ali's Mix - 2026 – Live", title)
    }

    @Test
    fun extractTitleFromJson_decodesEscapedQuotes() {
        val json = "{\"title\":\"He said \\\"hello\\\" - test\"}"

        val title = YouTubeMetadataParser.extractTitleFromJson(json)

        assertEquals("He said \"hello\" - test", title)
    }

    @Test
    fun extractTitleFromJson_handlesLogWrappedOutput() {
        val output = """
            [youtube] abc123: Downloading webpage
            {"id":"abc123","title":"Rock \u0026 Roll - 80\u0027s"}
            [download] Destination: file.mp4
        """.trimIndent()

        val title = YouTubeMetadataParser.extractTitleFromJson(output)

        assertEquals("Rock & Roll - 80's", title)
    }

    @Test
    fun extractTitleFromJson_returnsNullForMissingTitle() {
        val json = "{\"id\":\"abc123\"}"

        val title = YouTubeMetadataParser.extractTitleFromJson(json)

        assertNull(title)
    }
}
