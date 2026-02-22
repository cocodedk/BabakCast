package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XUrlExtractorTest {

    @Test
    fun detectsXComUrl() {
        val url = XUrlExtractor.extractXUrlFromText("https://x.com/user/status/123")
        assertEquals("https://x.com/user/status/123", url)
    }

    @Test
    fun detectsTwitterComUrl() {
        val url = XUrlExtractor.extractXUrlFromText("https://twitter.com/user/status/123")
        assertEquals("https://twitter.com/user/status/123", url)
    }

    @Test
    fun detectsMobileTwitterUrl() {
        val url = XUrlExtractor.extractXUrlFromText("https://mobile.twitter.com/user/status/123")
        assertEquals("https://mobile.twitter.com/user/status/123", url)
    }

    @Test
    fun extractsXUrlFromMixedText() {
        val text = "Check this out https://x.com/elonmusk/status/1234567890 amazing video"
        val url = XUrlExtractor.extractXUrlFromText(text)
        assertEquals("https://x.com/elonmusk/status/1234567890", url)
    }

    @Test
    fun skipsNonXUrlsAndFindsX() {
        val text = "See https://example.com then https://x.com/user/status/999"
        val url = XUrlExtractor.extractXUrlFromText(text)
        assertEquals("https://x.com/user/status/999", url)
    }

    @Test
    fun stripsTrailingPunctuation() {
        assertEquals(
            "https://x.com/user/status/123",
            XUrlExtractor.extractXUrlFromText("https://x.com/user/status/123,")
        )
        assertEquals(
            "https://x.com/user/status/123",
            XUrlExtractor.extractXUrlFromText("https://x.com/user/status/123)")
        )
        assertEquals(
            "https://x.com/user/status/123",
            XUrlExtractor.extractXUrlFromText("https://x.com/user/status/123;")
        )
    }

    @Test
    fun returnsNullForNonXUrl() {
        assertNull(XUrlExtractor.extractXUrlFromText("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun returnsNullForBlankText() {
        assertNull(XUrlExtractor.extractXUrlFromText("   "))
    }

    @Test
    fun returnsNullForNullText() {
        assertNull(XUrlExtractor.extractXUrlFromText(null))
    }
}
