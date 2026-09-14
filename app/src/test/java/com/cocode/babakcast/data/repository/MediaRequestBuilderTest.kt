package com.cocode.babakcast.data.repository

import com.cocode.babakcast.util.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestBuilderTest {

    // --- Info request ---

    @Test
    fun infoRequestYouTubeHasBasicOptions() {
        val request = MediaRepository.buildInfoRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE
        )
        assertTrue(request.hasOption("--skip-download"))
        assertTrue(request.hasOption("--dump-json"))
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun infoRequestYouTubeUsesPlayerClientExtractorArgs() {
        val request = MediaRepository.buildInfoRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE
        )
        assertTrue(request.hasOption("--extractor-args"))
        assertEquals(
            "youtube:player_client=default,-android_sdkless",
            request.getOption("--extractor-args")
        )
    }

    @Test
    fun infoRequestXUsesSyndicationApi() {
        val request = MediaRepository.buildInfoRequest(
            "https://x.com/user/status/123", Platform.X
        )
        assertTrue(request.hasOption("--extractor-args"))
        assertEquals("twitter:api=syndication", request.getOption("--extractor-args"))
    }

    @Test
    fun infoRequestXHasBasicOptions() {
        val request = MediaRepository.buildInfoRequest(
            "https://x.com/user/status/123", Platform.X
        )
        assertTrue(request.hasOption("--skip-download"))
        assertTrue(request.hasOption("--dump-json"))
        assertTrue(request.hasOption("--no-warnings"))
    }

    // --- Download request ---

    /**
     * A muxed stream must still win when one exists, so this is a no-op for the downloads
     * that work today; the video-only + audio-only pair is only a fallback for the day a
     * client chain stops serving combined streams.
     */
    @Test
    fun downloadRequestSelectsMuxedFirstThenFallsBackToMergedStreams() {
        for (platform in Platform.entries) {
            val request = MediaRepository.buildDownloadRequest(
                "https://example.com/watch?v=abc123", platform, "/tmp/out.mp4"
            )
            assertTrue(request.hasOption("-f"))
            assertEquals("best[ext=mp4]/best/" +
                "bv*[ext=mp4][vcodec^=avc1][height<=720]+ba[ext=m4a]/" +
                "bv*[ext=mp4][height<=720]+ba[ext=m4a]/" +
                "bv*+ba", request.getOption("-f"))
            // Merging a pair needs a container; yt-dlp ignores it when nothing merges.
            assertEquals("mp4", request.getOption("--merge-output-format"))
        }
    }

    @Test
    fun downloadRequestYouTubeUsesPlayerClientExtractorArgs() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--extractor-args"))
        assertEquals(
            "youtube:player_client=default,-android_sdkless",
            request.getOption("--extractor-args")
        )
    }

    @Test
    fun downloadRequestYouTubeSetsOutputPath() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertEquals("/tmp/out.mp4", request.getOption("-o"))
    }

    @Test
    fun downloadRequestYouTubeSuppressesWarnings() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun downloadRequestXUsesSyndicationApi() {
        val request = MediaRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--extractor-args"))
        assertEquals("twitter:api=syndication", request.getOption("--extractor-args"))
    }


    @Test
    fun downloadRequestXSuppressesWarnings() {
        val request = MediaRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun downloadRequestXSetsOutputPath() {
        val request = MediaRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertEquals("/tmp/out.mp4", request.getOption("-o"))
    }

    // --- Instagram info request ---

    @Test
    fun infoRequestInstagramHasBasicOptions() {
        val request = MediaRepository.buildInfoRequest(
            "https://www.instagram.com/reel/ABC123/", Platform.INSTAGRAM
        )
        assertTrue(request.hasOption("--skip-download"))
        assertTrue(request.hasOption("--dump-json"))
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun infoRequestInstagramNoExtractorArgs() {
        val request = MediaRepository.buildInfoRequest(
            "https://www.instagram.com/reel/ABC123/", Platform.INSTAGRAM
        )
        assertFalse(request.hasOption("--extractor-args"))
    }

    // --- Instagram download request ---


    @Test
    fun downloadRequestInstagramNoExtractorArgs() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.instagram.com/reel/ABC123/", Platform.INSTAGRAM, "/tmp/out.mp4"
        )
        assertFalse(request.hasOption("--extractor-args"))
    }

    @Test
    fun downloadRequestInstagramSetsOutputPath() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.instagram.com/reel/ABC123/", Platform.INSTAGRAM, "/tmp/out.mp4"
        )
        assertEquals("/tmp/out.mp4", request.getOption("-o"))
    }

    @Test
    fun downloadRequestInstagramSuppressesWarnings() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.instagram.com/reel/ABC123/", Platform.INSTAGRAM, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--no-warnings"))
    }

    // --- LinkedIn info request ---

    @Test
    fun infoRequestLinkedInHasBasicOptions() {
        val request = MediaRepository.buildInfoRequest(
            "https://www.linkedin.com/posts/test-1234567890123456789", Platform.LINKEDIN
        )
        assertTrue(request.hasOption("--skip-download"))
        assertTrue(request.hasOption("--dump-json"))
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun infoRequestLinkedInNoExtractorArgs() {
        val request = MediaRepository.buildInfoRequest(
            "https://www.linkedin.com/posts/test-1234567890123456789", Platform.LINKEDIN
        )
        assertFalse(request.hasOption("--extractor-args"))
    }

    // --- LinkedIn download request ---


    @Test
    fun downloadRequestLinkedInNoExtractorArgs() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.linkedin.com/posts/test-1234567890123456789", Platform.LINKEDIN, "/tmp/out.mp4"
        )
        assertFalse(request.hasOption("--extractor-args"))
    }

    @Test
    fun downloadRequestLinkedInSetsOutputPath() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.linkedin.com/posts/test-1234567890123456789", Platform.LINKEDIN, "/tmp/out.mp4"
        )
        assertEquals("/tmp/out.mp4", request.getOption("-o"))
    }

    @Test
    fun downloadRequestLinkedInSuppressesWarnings() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.linkedin.com/posts/test-1234567890123456789", Platform.LINKEDIN, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--no-warnings"))
    }
}
