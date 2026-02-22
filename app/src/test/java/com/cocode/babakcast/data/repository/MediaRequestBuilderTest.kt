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
    fun infoRequestYouTubeNoExtractorArgs() {
        val request = MediaRepository.buildInfoRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE
        )
        assertFalse(request.hasOption("--extractor-args"))
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

    @Test
    fun downloadRequestYouTubeHasMp4FormatSelector() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("-f"))
        assertEquals("best[ext=mp4]/best", request.getOption("-f"))
    }

    @Test
    fun downloadRequestYouTubeNoExtractorArgs() {
        val request = MediaRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertFalse(request.hasOption("--extractor-args"))
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
    fun downloadRequestXHasMp4FormatSelector() {
        val request = MediaRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("-f"))
        assertEquals("best[ext=mp4]/best", request.getOption("-f"))
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
}
