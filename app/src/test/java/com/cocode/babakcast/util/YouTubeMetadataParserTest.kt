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

    // --- extractDescriptionFromJson tests ---

    @Test
    fun extractDescriptionFromJson_returnsFullText() {
        val json = """{"title":"Truncated tweet...","description":"This is the full tweet text that was too long to fit in the title field"}"""

        val description = YouTubeMetadataParser.extractDescriptionFromJson(json)

        assertEquals("This is the full tweet text that was too long to fit in the title field", description)
    }

    @Test
    fun extractDescriptionFromJson_returnsNullWhenMissing() {
        val json = """{"title":"Some title","id":"abc123"}"""

        val description = YouTubeMetadataParser.extractDescriptionFromJson(json)

        assertNull(description)
    }

    @Test
    fun extractDescriptionFromJson_returnsNullForBlank() {
        val json = """{"title":"Some title","description":"   "}"""

        val description = YouTubeMetadataParser.extractDescriptionFromJson(json)

        assertNull(description)
    }

    @Test
    fun extractDescriptionFromJson_decodesUnicodeEscapes() {
        val json = """{"description":"Ali\u0027s full post \u2013 with unicode"}"""

        val description = YouTubeMetadataParser.extractDescriptionFromJson(json)

        assertEquals("Ali's full post – with unicode", description)
    }

    @Test
    fun extractDescriptionFromJson_handlesLogWrappedOutput() {
        val output = """
            [twitter] 123456: Downloading webpage
            {"id":"123456","title":"Short...","description":"The full tweet text here"}
            [download] Destination: file.mp4
        """.trimIndent()

        val description = YouTubeMetadataParser.extractDescriptionFromJson(output)

        assertEquals("The full tweet text here", description)
    }
}
