package com.cocode.babakcast.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TweetDownloadResultTest {

    @Test
    fun allFiles_combinesImagesAndVideos() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "test",
            imageFiles = listOf(File("img1.jpg"), File("img2.jpg")),
            videoFiles = listOf(File("video.mp4"))
        )

        assertEquals(3, result.allFiles.size)
        assertEquals("img1.jpg", result.allFiles[0].name)
        assertEquals("img2.jpg", result.allFiles[1].name)
        assertEquals("video.mp4", result.allFiles[2].name)
    }

    @Test
    fun isMixed_trueWhenBothTypesPresent() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "test",
            imageFiles = listOf(File("img.jpg")),
            videoFiles = listOf(File("video.mp4"))
        )

        assertTrue(result.isMixed)
        assertTrue(result.hasImages)
        assertTrue(result.hasVideos)
    }

    @Test
    fun isMixed_falseWhenOnlyImages() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "test",
            imageFiles = listOf(File("img.jpg")),
            videoFiles = emptyList()
        )

        assertFalse(result.isMixed)
        assertTrue(result.hasImages)
        assertFalse(result.hasVideos)
    }

    @Test
    fun isMixed_falseWhenOnlyVideos() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "test",
            imageFiles = emptyList(),
            videoFiles = listOf(File("video.mp4"))
        )

        assertFalse(result.isMixed)
        assertFalse(result.hasImages)
        assertTrue(result.hasVideos)
    }

    @Test
    fun allFiles_onlyImages() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "test",
            imageFiles = listOf(File("img1.jpg"), File("img2.png")),
            videoFiles = emptyList()
        )

        assertEquals(2, result.allFiles.size)
        assertEquals("img1.jpg", result.allFiles[0].name)
        assertEquals("img2.png", result.allFiles[1].name)
    }

    @Test
    fun allFiles_onlyVideos() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "test",
            imageFiles = emptyList(),
            videoFiles = listOf(File("vid1.mp4"), File("vid2.mp4"))
        )

        assertEquals(2, result.allFiles.size)
        assertEquals("vid1.mp4", result.allFiles[0].name)
        assertEquals("vid2.mp4", result.allFiles[1].name)
    }

    @Test
    fun allFiles_multipleImagesAndMultipleVideos() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "test",
            imageFiles = listOf(File("img1.jpg"), File("img2.jpg"), File("img3.jpg")),
            videoFiles = listOf(File("vid1.mp4"), File("vid2.mp4"))
        )

        assertEquals(5, result.allFiles.size)
        // images come first
        assertEquals("img1.jpg", result.allFiles[0].name)
        assertEquals("img2.jpg", result.allFiles[1].name)
        assertEquals("img3.jpg", result.allFiles[2].name)
        // then videos
        assertEquals("vid1.mp4", result.allFiles[3].name)
        assertEquals("vid2.mp4", result.allFiles[4].name)
    }

    @Test
    fun textField_preserved() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "Hello world! \uD83C\uDF0D",
            imageFiles = listOf(File("img.jpg"))
        )

        assertEquals("Hello world! \uD83C\uDF0D", result.text)
    }

    @Test
    fun tweetId_preserved() {
        val result = TweetDownloadResult(
            tweetId = "1234567890123456789",
            text = "test"
        )

        assertEquals("1234567890123456789", result.tweetId)
    }

    @Test
    fun emptyResult() {
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "text only tweet"
        )

        assertTrue(result.allFiles.isEmpty())
        assertFalse(result.hasImages)
        assertFalse(result.hasVideos)
        assertFalse(result.isMixed)
    }
}
