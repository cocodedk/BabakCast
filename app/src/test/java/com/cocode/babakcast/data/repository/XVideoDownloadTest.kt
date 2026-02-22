package com.cocode.babakcast.data.repository

import com.cocode.babakcast.util.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XVideoDownloadTest {

    // --- Info request ---

    @Test
    fun infoRequest_youTube_hasBasicOptions() {
        val request = YouTubeRepository.buildInfoRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE
        )
        assertTrue(request.hasOption("--skip-download"))
        assertTrue(request.hasOption("--dump-json"))
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun infoRequest_youTube_noExtractorArgs() {
        val request = YouTubeRepository.buildInfoRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE
        )
        assertFalse(request.hasOption("--extractor-args"))
    }

    @Test
    fun infoRequest_x_usesSyndicationApi() {
        val request = YouTubeRepository.buildInfoRequest(
            "https://x.com/user/status/123", Platform.X
        )
        assertTrue(request.hasOption("--extractor-args"))
        assertEquals("twitter:api=syndication", request.getOption("--extractor-args"))
    }

    @Test
    fun infoRequest_x_hasBasicOptions() {
        val request = YouTubeRepository.buildInfoRequest(
            "https://x.com/user/status/123", Platform.X
        )
        assertTrue(request.hasOption("--skip-download"))
        assertTrue(request.hasOption("--dump-json"))
        assertTrue(request.hasOption("--no-warnings"))
    }

    // --- Download request ---

    @Test
    fun downloadRequest_youTube_hasMp4FormatSelector() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("-f"))
        assertEquals("best[ext=mp4]/best", request.getOption("-f"))
    }

    @Test
    fun downloadRequest_youTube_noExtractorArgs() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertFalse(request.hasOption("--extractor-args"))
    }

    @Test
    fun downloadRequest_youTube_setsOutputPath() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertEquals("/tmp/out.mp4", request.getOption("-o"))
    }

    @Test
    fun downloadRequest_youTube_suppressesWarnings() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://www.youtube.com/watch?v=abc123", Platform.YOUTUBE, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun downloadRequest_x_usesSyndicationApi() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--extractor-args"))
        assertEquals("twitter:api=syndication", request.getOption("--extractor-args"))
    }

    @Test
    fun downloadRequest_x_hasMp4FormatSelector() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("-f"))
        assertEquals("best[ext=mp4]/best", request.getOption("-f"))
    }

    @Test
    fun downloadRequest_x_suppressesWarnings() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertTrue(request.hasOption("--no-warnings"))
    }

    @Test
    fun downloadRequest_x_setsOutputPath() {
        val request = YouTubeRepository.buildDownloadRequest(
            "https://x.com/user/status/123", Platform.X, "/tmp/out.mp4"
        )
        assertEquals("/tmp/out.mp4", request.getOption("-o"))
    }
}
