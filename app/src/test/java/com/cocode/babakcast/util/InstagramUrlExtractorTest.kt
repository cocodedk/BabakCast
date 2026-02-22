package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramUrlExtractorTest {

    @Test
    fun detectsInstagramComUrl() {
        val url = InstagramUrlExtractor.extractInstagramUrlFromText("https://www.instagram.com/p/ABC123/")
        assertEquals("https://www.instagram.com/p/ABC123/", url)
    }

    @Test
    fun detectsInstagramComWithoutWww() {
        val url = InstagramUrlExtractor.extractInstagramUrlFromText("https://instagram.com/reel/XYZ789/")
        assertEquals("https://instagram.com/reel/XYZ789/", url)
    }

    @Test
    fun detectsInstagrAmShortUrl() {
        val url = InstagramUrlExtractor.extractInstagramUrlFromText("https://instagr.am/p/ABC123/")
        assertEquals("https://instagr.am/p/ABC123/", url)
    }

    @Test
    fun extractsInstagramUrlFromMixedText() {
        val text = "Check this out https://www.instagram.com/reel/DEF456/ amazing video"
        val url = InstagramUrlExtractor.extractInstagramUrlFromText(text)
        assertEquals("https://www.instagram.com/reel/DEF456/", url)
    }

    @Test
    fun skipsNonInstagramUrlsAndFindsInstagram() {
        val text = "See https://example.com then https://instagram.com/p/ABC123/"
        val url = InstagramUrlExtractor.extractInstagramUrlFromText(text)
        assertEquals("https://instagram.com/p/ABC123/", url)
    }

    @Test
    fun stripsTrailingPunctuation() {
        assertEquals(
            "https://instagram.com/p/ABC123/",
            InstagramUrlExtractor.extractInstagramUrlFromText("https://instagram.com/p/ABC123/,")
        )
        assertEquals(
            "https://instagram.com/p/ABC123/",
            InstagramUrlExtractor.extractInstagramUrlFromText("https://instagram.com/p/ABC123/)")
        )
        assertEquals(
            "https://instagram.com/p/ABC123/",
            InstagramUrlExtractor.extractInstagramUrlFromText("https://instagram.com/p/ABC123/;")
        )
    }

    @Test
    fun returnsNullForNonInstagramUrl() {
        assertNull(InstagramUrlExtractor.extractInstagramUrlFromText("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun returnsNullForBlankText() {
        assertNull(InstagramUrlExtractor.extractInstagramUrlFromText("   "))
    }

    @Test
    fun returnsNullForNullText() {
        assertNull(InstagramUrlExtractor.extractInstagramUrlFromText(null))
    }

    // --- isInstagramUrl direct tests ---

    @Test
    fun isInstagramUrlReturnsTrueForInstagramCom() {
        assertTrue(InstagramUrlExtractor.isInstagramUrl("https://www.instagram.com/p/ABC123/"))
    }

    @Test
    fun isInstagramUrlReturnsTrueForInstagrAm() {
        assertTrue(InstagramUrlExtractor.isInstagramUrl("https://instagr.am/p/ABC123/"))
    }

    @Test
    fun isInstagramUrlReturnsTrueForInstagramWithoutWww() {
        assertTrue(InstagramUrlExtractor.isInstagramUrl("https://instagram.com/reel/ABC123/"))
    }

    @Test
    fun isInstagramUrlReturnsFalseForYouTube() {
        assertFalse(InstagramUrlExtractor.isInstagramUrl("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun isInstagramUrlReturnsFalseForEmpty() {
        assertFalse(InstagramUrlExtractor.isInstagramUrl(""))
    }

    @Test
    fun isInstagramUrlReturnsTrueForMobileInstagram() {
        assertTrue(InstagramUrlExtractor.isInstagramUrl("https://m.instagram.com/reel/ABC123/"))
    }

    @Test
    fun isInstagramUrlReturnsTrueForLinkInstagram() {
        assertTrue(InstagramUrlExtractor.isInstagramUrl("https://l.instagram.com/p/ABC123/"))
    }

    @Test
    fun isInstagramUrlReturnsFalseForLookalikeDomain() {
        assertFalse(InstagramUrlExtractor.isInstagramUrl("https://notinstagram.com/p/ABC123/"))
        assertFalse(InstagramUrlExtractor.isInstagramUrl("https://fakeinstagr.am/p/ABC123/"))
    }

    @Test
    fun extractsMobileInstagramUrlFromText() {
        val url = InstagramUrlExtractor.extractInstagramUrlFromText("Check https://m.instagram.com/reel/ABC123/ out")
        assertEquals("https://m.instagram.com/reel/ABC123/", url)
    }

    @Test
    fun extractsLinkInstagramUrlFromText() {
        val url = InstagramUrlExtractor.extractInstagramUrlFromText("See https://l.instagram.com/p/ABC123/ here")
        assertEquals("https://l.instagram.com/p/ABC123/", url)
    }

    @Test
    fun stripsMultipleTrailingPunctuation() {
        assertEquals(
            "https://instagram.com/p/ABC123/",
            InstagramUrlExtractor.extractInstagramUrlFromText("https://instagram.com/p/ABC123/).")
        )
    }
}
