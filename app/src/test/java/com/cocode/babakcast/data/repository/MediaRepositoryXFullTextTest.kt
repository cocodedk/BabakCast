package com.cocode.babakcast.data.repository

import com.cocode.babakcast.util.Platform
import com.cocode.babakcast.util.YouTubeMetadataParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that X/Twitter posts prefer the full `description` field over
 * the truncated `title`, while other platforms keep using `title`.
 */
class MediaRepositoryXFullTextTest {

    /**
     * Mirrors the title-resolution logic in [MediaRepository.getVideoInfo].
     */
    private fun resolveTitle(jsonOutput: String, platform: Platform): String {
        return if (platform == Platform.X) {
            YouTubeMetadataParser.extractDescriptionFromJson(jsonOutput)
                ?: YouTubeMetadataParser.extractTitleFromJson(jsonOutput)
                ?: "Video"
        } else {
            YouTubeMetadataParser.extractTitleFromJson(jsonOutput) ?: "Video"
        }
    }

    @Test
    fun xPlatform_prefersDescriptionOverTitle() {
        val json = """{"title":"Truncated tweet te...","description":"Truncated tweet text that is actually very long and contains the full post content"}"""

        val title = resolveTitle(json, Platform.X)

        assertEquals(
            "Truncated tweet text that is actually very long and contains the full post content",
            title
        )
    }

    @Test
    fun xPlatform_fallsBackToTitleWhenNoDescription() {
        val json = """{"title":"Short tweet","id":"123"}"""

        val title = resolveTitle(json, Platform.X)

        assertEquals("Short tweet", title)
    }

    @Test
    fun youTubePlatform_usesTitle() {
        val json = """{"title":"YouTube Video Title","description":"Full YouTube description that should be ignored"}"""

        val title = resolveTitle(json, Platform.YOUTUBE)

        assertEquals("YouTube Video Title", title)
    }

    @Test
    fun instagramPlatform_usesTitle() {
        val json = """{"title":"Instagram Reel Title","description":"Full Instagram caption that should be ignored"}"""

        val title = resolveTitle(json, Platform.INSTAGRAM)

        assertEquals("Instagram Reel Title", title)
    }
}
