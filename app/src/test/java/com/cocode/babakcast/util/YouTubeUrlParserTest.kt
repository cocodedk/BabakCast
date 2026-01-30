package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun extractsVideoIdFromWatchUrl() {
        val id = YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=abc123XYZ")
        assertEquals("abc123XYZ", id)
    }

    @Test
    fun extractsVideoIdFromWatchUrlWithExtraParams() {
        val id = YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?list=PL123&v=abc123XYZ&t=10")
        assertEquals("abc123XYZ", id)
    }

    @Test
    fun extractsVideoIdFromShortUrl() {
        val id = YouTubeUrlParser.extractVideoId("https://youtu.be/abc123XYZ")
        assertEquals("abc123XYZ", id)
    }

    @Test
    fun extractsVideoIdFromEmbedUrl() {
        val id = YouTubeUrlParser.extractVideoId("https://www.youtube.com/embed/abc123XYZ")
        assertEquals("abc123XYZ", id)
    }

    @Test
    fun extractsVideoIdFromShortsUrl() {
        val id = YouTubeUrlParser.extractVideoId("https://www.youtube.com/shorts/abc123XYZ")
        assertEquals("abc123XYZ", id)
    }

    @Test
    fun extractsVideoIdFromShortsUrlWithQuery() {
        val id = YouTubeUrlParser.extractVideoId("https://youtube.com/shorts/abc123XYZ?feature=share")
        assertEquals("abc123XYZ", id)
    }

    @Test
    fun returnsNullForNonYouTubeUrl() {
        val id = YouTubeUrlParser.extractVideoId("https://example.com/watch?v=abc123XYZ")
        assertNull(id)
    }

    @Test
    fun returnsNullForRandomText() {
        val id = YouTubeUrlParser.extractVideoId("not a url")
        assertNull(id)
    }
}
