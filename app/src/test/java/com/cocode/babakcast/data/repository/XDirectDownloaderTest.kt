package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.model.TweetMedia
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for [XDirectDownloader] — verifies actual HTTP download behaviour.
 * Uses MockWebServer so no real network calls are made.
 */
class XDirectDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: XDirectDownloader
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = XDirectDownloader(OkHttpClient(), "TEST")
        tempDir = Files.createTempDirectory("xdl_test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    // --- downloadPhoto ---

    @Test
    fun downloadPhoto_writesBodyToOutputFile() {
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) // JPEG magic
        server.enqueue(MockResponse().setBody(Buffer().write(imageBytes)).setResponseCode(200))

        val photo = TweetMedia.Photo(
            url = server.url("/media/img.jpg?name=large").toString(),
            originalUrl = server.url("/media/img.jpg").toString()
        )
        val output = File(tempDir, "img.jpg")

        val result = downloader.downloadPhoto(photo, output)

        assertTrue("downloadPhoto should succeed", result.isSuccess)
        assertTrue("output file must exist", output.exists())
        assertArrayEquals("file bytes must match response body", imageBytes, output.readBytes())
    }

    @Test
    fun downloadPhoto_http404_returnsFailureAndNoFile() {
        server.enqueue(MockResponse().setResponseCode(404))

        val photo = TweetMedia.Photo(
            url = server.url("/media/missing.jpg").toString(),
            originalUrl = server.url("/media/missing.jpg").toString()
        )
        val output = File(tempDir, "missing.jpg")

        val result = downloader.downloadPhoto(photo, output)

        assertTrue("HTTP 404 should be a failure", result.isFailure)
        assertFalse("no output file should be created on 404", output.exists())
    }

    @Test
    fun downloadTwoPhotos_bothFilesCreated() {
        // Simulates downloading two images from a tweet with 2 photos
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(4, 5, 6)
        server.enqueue(MockResponse().setBody(Buffer().write(bytes1)).setResponseCode(200))
        server.enqueue(MockResponse().setBody(Buffer().write(bytes2)).setResponseCode(200))

        val photo1 = TweetMedia.Photo(
            url = server.url("/media/HEVBvNbXAAAXz99.jpg?name=large").toString(),
            originalUrl = server.url("/media/HEVBvNbXAAAXz99.jpg").toString()
        )
        val photo2 = TweetMedia.Photo(
            url = server.url("/media/HEVBvyNboAAc3Hk.jpg?name=large").toString(),
            originalUrl = server.url("/media/HEVBvyNboAAc3Hk.jpg").toString()
        )

        val file1 = File(tempDir, "tweet_123_img1.jpg")
        val file2 = File(tempDir, "tweet_123_img2.jpg")

        val r1 = downloader.downloadPhoto(photo1, file1)
        val r2 = downloader.downloadPhoto(photo2, file2)

        assertTrue("first photo download must succeed", r1.isSuccess)
        assertTrue("second photo download must succeed", r2.isSuccess)
        assertTrue("first file must exist", file1.exists())
        assertTrue("second file must exist", file2.exists())
        assertArrayEquals(bytes1, file1.readBytes())
        assertArrayEquals(bytes2, file2.readBytes())
    }

    @Test
    fun downloadPhoto_partialFailure_successfulFilesStillCreated() {
        // First photo fails, second succeeds — imageFiles should contain only the second
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(7, 8, 9))).setResponseCode(200))

        val photo1 = TweetMedia.Photo(
            url = server.url("/media/img1.jpg?name=large").toString(),
            originalUrl = server.url("/media/img1.jpg").toString()
        )
        val photo2 = TweetMedia.Photo(
            url = server.url("/media/img2.jpg?name=large").toString(),
            originalUrl = server.url("/media/img2.jpg").toString()
        )

        val file1 = File(tempDir, "tweet_123_img1.jpg")
        val file2 = File(tempDir, "tweet_123_img2.jpg")

        val r1 = downloader.downloadPhoto(photo1, file1)
        val r2 = downloader.downloadPhoto(photo2, file2)

        assertTrue("HTTP 500 should be a failure", r1.isFailure)
        assertFalse("failed download must not create file", file1.exists())
        assertTrue("second photo should succeed", r2.isSuccess)
        assertTrue("second file must exist", file2.exists())
    }

    // --- downloadVideo ---

    @Test
    fun downloadVideo_writesBodyToOutputFile() {
        val videoBytes = byteArrayOf(0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70) // mp4 magic
        server.enqueue(MockResponse().setBody(Buffer().write(videoBytes)).setResponseCode(200))

        val directUrl = server.url("/video/tweet_123_vid1.mp4").toString()
        val output = File(tempDir, "tweet_123_vid1.mp4")

        val result = downloader.downloadVideo(directUrl, output)

        assertTrue("downloadVideo should succeed", result.isSuccess)
        assertTrue("output file must exist", output.exists())
        assertArrayEquals(videoBytes, output.readBytes())
    }

    @Test
    fun downloadVideo_http403_returnsFailure() {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = downloader.downloadVideo(
            server.url("/video/forbidden.mp4").toString(),
            File(tempDir, "forbidden.mp4")
        )

        assertTrue("HTTP 403 should be a failure", result.isFailure)
    }

    // --- guessImageExtension ---

    @Test
    fun guessImageExtension_jpgNoQueryParams() {
        val ext = XDirectDownloader.guessImageExtension(
            "https://pbs.twimg.com/media/HEVBvNbXAAAXz99.jpg"
        )
        org.junit.Assert.assertEquals("jpg", ext)
    }

    @Test
    fun guessImageExtension_pngWithNameLargeParam() {
        val ext = XDirectDownloader.guessImageExtension(
            "https://pbs.twimg.com/media/ABC.png?name=large"
        )
        org.junit.Assert.assertEquals("png", ext)
    }

    @Test
    fun guessImageExtension_webp() {
        val ext = XDirectDownloader.guessImageExtension(
            "https://pbs.twimg.com/media/ABC.WEBP?name=large"
        )
        org.junit.Assert.assertEquals("webp", ext)
    }

    @Test
    fun guessImageExtension_unknownDefaultsToJpg() {
        val ext = XDirectDownloader.guessImageExtension(
            "https://pbs.twimg.com/media/ABC.bmp"
        )
        org.junit.Assert.assertEquals("jpg", ext)
    }
}
