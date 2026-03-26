package com.cocode.babakcast.util.urlparsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramUrlParserTest {

    @Test
    fun extractsShortcodeFromPostUrl() {
        val id = InstagramUrlParser.extractShortcode("https://www.instagram.com/p/ABC123def/")
        assertEquals("ABC123def", id)
    }

    @Test
    fun extractsShortcodeFromReelUrl() {
        val id = InstagramUrlParser.extractShortcode("https://www.instagram.com/reel/XYZ789abc/")
        assertEquals("XYZ789abc", id)
    }

    @Test
    fun extractsShortcodeFromReelsUrl() {
        val id = InstagramUrlParser.extractShortcode("https://www.instagram.com/reels/XYZ789abc/")
        assertEquals("XYZ789abc", id)
    }

    @Test
    fun extractsShortcodeFromTvUrl() {
        val id = InstagramUrlParser.extractShortcode("https://www.instagram.com/tv/DEF456ghi/")
        assertEquals("DEF456ghi", id)
    }

    @Test
    fun extractsShortcodeWithoutWww() {
        val id = InstagramUrlParser.extractShortcode("https://instagram.com/p/ABC123def/")
        assertEquals("ABC123def", id)
    }

    @Test
    fun extractsShortcodeFromInstagrAm() {
        val id = InstagramUrlParser.extractShortcode("https://instagr.am/p/ABC123def/")
        assertEquals("ABC123def", id)
    }

    @Test
    fun handlesUrlWithQueryParams() {
        val id = InstagramUrlParser.extractShortcode("https://www.instagram.com/p/ABC123def/?utm_source=share")
        assertEquals("ABC123def", id)
    }

    @Test
    fun handlesUrlWithoutTrailingSlash() {
        val id = InstagramUrlParser.extractShortcode("https://www.instagram.com/p/ABC123def")
        assertEquals("ABC123def", id)
    }

    @Test
    fun returnsNullForProfileUrl() {
        assertNull(InstagramUrlParser.extractShortcode("https://www.instagram.com/username"))
    }

    @Test
    fun returnsNullForNonInstagramUrl() {
        assertNull(InstagramUrlParser.extractShortcode("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun returnsNullForRandomText() {
        assertNull(InstagramUrlParser.extractShortcode("not a url"))
    }

    @Test
    fun extractsShortcodeWithHyphenAndUnderscore() {
        val id = InstagramUrlParser.extractShortcode("https://www.instagram.com/p/AB-CD_12/")
        assertEquals("AB-CD_12", id)
    }

    @Test
    fun returnsNullForEmptyString() {
        assertNull(InstagramUrlParser.extractShortcode(""))
    }

    @Test
    fun returnsNullForStoryUrl() {
        assertNull(InstagramUrlParser.extractShortcode("https://www.instagram.com/stories/username/123456789/"))
    }
}
