package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression test for the bug where title extraction succeeds but getVideoInfo() fails.
 *
 * From logs:
 * - extractTitleFromJson: SUCCESS via parseJsonTitle (full): 'Scientists Discover How To Regrow Cartilage'
 * - BUT metadataResult.isSuccess: false
 *
 * This means an exception is thrown after title extraction, likely in extractChaptersFromJson().
 */
class YouTubeMetadataParserRegressionTest {

    /**
     * Actual JSON output from yt-dlp for video QCMXvU4tYp0
     * This is the JSON that successfully extracts the title but causes getVideoInfo() to fail.
     */
    private val actualYtDlpOutput = """
        {"id": "QCMXvU4tYp0", "title": "Scientists Discover How To Regrow Cartilage", "formats": [{"format_id": "sb2", "format_note": "storyboard", "ext": "mhtml", "protocol": "mhtml", "acodec": "none", "vcodec": "none", "url": "https://i.ytimg.com/sb/QCMXvU4tYp0/storyboard3_L1/M${'$'}M.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgiJ_vzLBg==&sigh=rs${'$'}AOn4CLAUizN4Nk_Dy6W-Zg7W1KFyywTTng", "width": 25, "height": 45}]}
    """.trimIndent()

    @Test
    fun actualYtDlpOutput_titleExtractionSucceeds() {
        val title = YouTubeMetadataParser.extractTitleFromJson(actualYtDlpOutput)

        assertNotNull("Title should be extracted from actual yt-dlp output", title)
        assertEquals(
            "Should extract the correct title",
            "Scientists Discover How To Regrow Cartilage",
            title
        )
    }

    @Test
    fun actualYtDlpOutput_chaptersExtractionDoesNotThrow() {
        // This test verifies that extractChaptersFromJson doesn't throw an exception
        // even when the JSON doesn't have chapters

        try {
            val chapters = YouTubeMetadataParser.extractChaptersFromJson(actualYtDlpOutput)

            // Should return empty list, not throw exception
            assertNotNull("Chapters should not be null", chapters)
            assertEquals(
                "Should return empty list when no chapters present",
                0,
                chapters.size
            )
        } catch (e: Exception) {
            throw AssertionError(
                "extractChaptersFromJson should NOT throw exception on valid JSON without chapters. " +
                "Exception: ${e.javaClass.name}: ${e.message}",
                e
            )
        }
    }

    @Test
    fun actualYtDlpOutput_bothTitleAndChaptersCanBeExtracted() {
        // This simulates what getVideoInfo() does
        try {
            val title = YouTubeMetadataParser.extractTitleFromJson(actualYtDlpOutput)
            val chapters = YouTubeMetadataParser.extractChaptersFromJson(actualYtDlpOutput)

            assertNotNull("Title should be extracted", title)
            assertEquals("Scientists Discover How To Regrow Cartilage", title)

            assertNotNull("Chapters should not be null", chapters)
            assertEquals("Should have 0 chapters", 0, chapters.size)
        } catch (e: Exception) {
            throw AssertionError(
                "Processing yt-dlp output should not throw exception. " +
                "This is the bug causing getVideoInfo() to fail! " +
                "Exception: ${e.javaClass.name}: ${e.message}",
                e
            )
        }
    }

    @Test
    fun largeJsonOutput_shouldNotCausePerformanceIssues() {
        // The actual output is 735KB - this tests handling of large JSON
        val largeJson = """
            {"id": "test", "title": "Test Video", "formats": [${generateLargeFormatsArray()}]}
        """.trimIndent()

        val title = YouTubeMetadataParser.extractTitleFromJson(largeJson)
        val chapters = YouTubeMetadataParser.extractChaptersFromJson(largeJson)

        assertEquals("Test Video", title)
        assertNotNull(chapters)
    }

    private fun generateLargeFormatsArray(): String {
        // Generate a large formats array similar to real yt-dlp output
        return (1..100).joinToString(",") { i ->
            """{"format_id": "format$i", "ext": "mp4", "width": 1920, "height": 1080}"""
        }
    }

    @Test
    fun jsonWithChapters_extractsChaptersCorrectly() {
        val jsonWithChapters = """
            {
                "id": "test123",
                "title": "Video With Chapters",
                "chapters": [
                    {"title": "Intro", "start_time": 0.0, "end_time": 30.0},
                    {"title": "Main Content", "start_time": 30.0, "end_time": 120.0},
                    {"title": "Outro", "start_time": 120.0, "end_time": 150.0}
                ]
            }
        """.trimIndent()

        val title = YouTubeMetadataParser.extractTitleFromJson(jsonWithChapters)
        val chapters = YouTubeMetadataParser.extractChaptersFromJson(jsonWithChapters)

        assertEquals("Video With Chapters", title)
        assertEquals(3, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals("Main Content", chapters[1].title)
        assertEquals("Outro", chapters[2].title)
    }

    @Test
    fun jsonWithoutChaptersField_returnsEmptyList() {
        val jsonWithoutChapters = """
            {"id": "test", "title": "No Chapters Video"}
        """.trimIndent()

        val chapters = YouTubeMetadataParser.extractChaptersFromJson(jsonWithoutChapters)

        assertNotNull("Should return empty list, not null", chapters)
        assertEquals(0, chapters.size)
    }

    @Test
    fun jsonWithNullChapters_returnsEmptyListNotException() {
        // This is the actual bug - YouTube API returns "chapters": null
        val jsonWithNullChapters = """
            {"id": "test", "title": "Video", "chapters": null}
        """.trimIndent()

        try {
            val chapters = YouTubeMetadataParser.extractChaptersFromJson(jsonWithNullChapters)
            assertNotNull("Should return empty list when chapters is null", chapters)
            assertEquals(0, chapters.size)
        } catch (e: Exception) {
            throw AssertionError(
                "Should handle null chapters gracefully. This was the production bug! " +
                "Exception: ${e.javaClass.name}: ${e.message}",
                e
            )
        }
    }

}
