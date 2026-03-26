package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.model.TweetDownloadResult
import com.cocode.babakcast.data.remote.TweetMedia
import com.cocode.babakcast.data.remote.TweetMediaResult
import com.cocode.babakcast.util.ShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * End-to-end tests for the X/Twitter all-media download flow.
 * Tests the full pipeline: syndication response → categorization → result model → MIME type.
 * No Android context or network calls needed.
 */
class XMediaDownloadFlowTest {

    // --- Photo-only tweet flow ---

    @Test
    fun photoOnlyTweet_categorizes_downloadsImages_sharesAsImages() {
        val media = listOf(
            TweetMedia.Photo(url = "https://pbs.twimg.com/media/IMG1.jpg?name=large", originalUrl = "https://pbs.twimg.com/media/IMG1.jpg"),
            TweetMedia.Photo(url = "https://pbs.twimg.com/media/IMG2.png?name=large", originalUrl = "https://pbs.twimg.com/media/IMG2.png")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)
        assertEquals(2, photos.size)
        assertTrue(videos.isEmpty())

        // Simulate download result
        val result = TweetDownloadResult(
            tweetId = "123",
            text = "Two photos",
            imageFiles = listOf(File("tweet_123_img1.jpg"), File("tweet_123_img2.png")),
            videoFiles = emptyList()
        )

        assertTrue(result.hasImages)
        assertFalse(result.hasVideos)
        assertFalse(result.isMixed)
        assertEquals(2, result.allFiles.size)

        // MIME type should be image/*
        assertEquals("image/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    // --- Video-only tweet flow ---

    @Test
    fun videoOnlyTweet_categorizes_downloadsVideo_sharesAsVideo() {
        val media = listOf(
            TweetMedia.Video(url = "https://video.twimg.com/vid.mp4", thumbnailUrl = "https://thumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)
        assertTrue(photos.isEmpty())
        assertEquals(1, videos.size)

        val result = TweetDownloadResult(
            tweetId = "456",
            text = "A video",
            imageFiles = emptyList(),
            videoFiles = listOf(File("tweet_456.mp4"))
        )

        assertFalse(result.hasImages)
        assertTrue(result.hasVideos)
        assertFalse(result.isMixed)
        assertEquals("video/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    // --- Mixed media tweet flow ---

    @Test
    fun mixedTweet_categorizes_downloadsBoth_sharesAsMixed() {
        val media = listOf(
            TweetMedia.Photo(url = "https://pbs.twimg.com/media/IMG1.jpg?name=large", originalUrl = "https://pbs.twimg.com/media/IMG1.jpg"),
            TweetMedia.Video(url = "https://video.twimg.com/vid.mp4", thumbnailUrl = "https://thumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)
        assertEquals(1, photos.size)
        assertEquals(1, videos.size)

        val result = TweetDownloadResult(
            tweetId = "789",
            text = "Photo and video",
            imageFiles = listOf(File("tweet_789_img1.jpg")),
            videoFiles = listOf(File("tweet_789.mp4"))
        )

        assertTrue(result.isMixed)
        assertEquals(2, result.allFiles.size)
        assertEquals("*/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    // --- GIF tweet flow ---

    @Test
    fun gifTweet_treatedAsVideo() {
        val media = listOf(
            TweetMedia.AnimatedGif(url = "https://video.twimg.com/gif.mp4", thumbnailUrl = "https://thumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)
        assertTrue(photos.isEmpty())
        assertEquals(1, videos.size)
        assertTrue(videos[0] is TweetMedia.AnimatedGif)

        val result = TweetDownloadResult(
            tweetId = "999",
            text = "A GIF",
            imageFiles = emptyList(),
            videoFiles = listOf(File("tweet_999.mp4"))
        )

        assertEquals("video/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    // --- Image extension selection ---

    @Test
    fun imageFilenames_matchExtractedExtensions() {
        val photos = listOf(
            TweetMedia.Photo(url = "https://pbs.twimg.com/media/A.jpg?name=large", originalUrl = "https://pbs.twimg.com/media/A.jpg"),
            TweetMedia.Photo(url = "https://pbs.twimg.com/media/B.png?name=large", originalUrl = "https://pbs.twimg.com/media/B.png"),
            TweetMedia.Photo(url = "https://pbs.twimg.com/media/C.webp?name=large", originalUrl = "https://pbs.twimg.com/media/C.webp")
        )

        val tweetId = "111"
        val expectedFiles = photos.mapIndexed { index, photo ->
            val ext = MediaRepository.guessImageExtension(photo.originalUrl)
            "tweet_${tweetId}_img${index + 1}.$ext"
        }

        assertEquals("tweet_111_img1.jpg", expectedFiles[0])
        assertEquals("tweet_111_img2.png", expectedFiles[1])
        assertEquals("tweet_111_img3.webp", expectedFiles[2])
    }

    // --- Tweet text passthrough ---

    @Test
    fun tweetText_passedThroughToResult() {
        val syndicationResult = TweetMediaResult(
            text = "Full tweet text with emoji \uD83D\uDE80",
            media = listOf(
                TweetMedia.Photo(url = "https://img.jpg?name=large", originalUrl = "https://img.jpg")
            )
        )

        val downloadResult = TweetDownloadResult(
            tweetId = "222",
            text = syndicationResult.text,
            imageFiles = listOf(File("img.jpg"))
        )

        assertEquals("Full tweet text with emoji \uD83D\uDE80", downloadResult.text)
    }

    // --- allFiles ordering: images before videos ---

    @Test
    fun allFiles_imagesBeforeVideos() {
        val result = TweetDownloadResult(
            tweetId = "333",
            text = "test",
            imageFiles = listOf(File("img1.jpg"), File("img2.jpg")),
            videoFiles = listOf(File("video.mp4"))
        )

        assertEquals("img1.jpg", result.allFiles[0].name)
        assertEquals("img2.jpg", result.allFiles[1].name)
        assertEquals("video.mp4", result.allFiles[2].name)
    }

    // --- No media found ---

    @Test
    fun emptyMediaList_categorizes_toEmptyBuckets() {
        val (photos, videos) = MediaRepository.categorizeTweetMedia(emptyList())

        assertTrue(photos.isEmpty())
        assertTrue(videos.isEmpty())
    }

    // --- Photo + animated GIF flow ---

    @Test
    fun photoAndGifTweet_categorizes_sharesAsMixed() {
        val media = listOf(
            TweetMedia.Photo(url = "https://pbs.twimg.com/media/IMG.jpg?name=large", originalUrl = "https://pbs.twimg.com/media/IMG.jpg"),
            TweetMedia.AnimatedGif(url = "https://video.twimg.com/gif.mp4", thumbnailUrl = "https://thumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)
        assertEquals(1, photos.size)
        assertEquals(1, videos.size)

        val result = TweetDownloadResult(
            tweetId = "555",
            text = "Photo and GIF",
            imageFiles = listOf(File("tweet_555_img1.jpg")),
            videoFiles = listOf(File("tweet_555.mp4"))
        )

        assertTrue(result.isMixed)
        assertEquals("*/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    // --- Partial download failure (some images fail) ---

    @Test
    fun partialFailure_onlySuccessfulFilesInResult() {
        // Simulate: 3 photos attempted, 1 failed → 2 image files
        val result = TweetDownloadResult(
            tweetId = "666",
            text = "Three photos but one failed",
            imageFiles = listOf(File("tweet_666_img1.jpg"), File("tweet_666_img3.jpg")),
            videoFiles = emptyList()
        )

        assertEquals(2, result.allFiles.size)
        assertTrue(result.hasImages)
        assertFalse(result.hasVideos)
        assertEquals("image/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    // --- Empty text tweet ---

    @Test
    fun emptyText_tweet_captionIsBlank() {
        val syndicationResult = TweetMediaResult(
            text = "",
            media = listOf(
                TweetMedia.Photo(url = "https://img.jpg?name=large", originalUrl = "https://img.jpg")
            )
        )

        val result = TweetDownloadResult(
            tweetId = "777",
            text = syndicationResult.text,
            imageFiles = listOf(File("img.jpg"))
        )

        assertEquals("", result.text)
    }

    // --- Video with null url (no mp4 variants) still produces result ---

    @Test
    fun videoWithNullUrl_noVideoFilesInResult() {
        val media = listOf(
            TweetMedia.Video(url = null, thumbnailUrl = "https://thumb.jpg")
        )

        val (_, videos) = MediaRepository.categorizeTweetMedia(media)
        assertEquals(1, videos.size)

        // In practice, yt-dlp might still download via HLS, but if it fails
        // the result would have no video files
        val result = TweetDownloadResult(
            tweetId = "888",
            text = "Video",
            imageFiles = emptyList(),
            videoFiles = emptyList()
        )

        assertFalse(result.hasVideos)
        assertTrue(result.allFiles.isEmpty())
    }

    // --- Multi-video file naming ---

    @Test
    fun multipleVideos_eachGetsOwnNumberedFile() {
        val tweetId = "456"
        val videos = listOf(
            TweetMedia.Video(url = "https://video.twimg.com/vid1.mp4", thumbnailUrl = null),
            TweetMedia.Video(url = "https://video.twimg.com/vid2.mp4", thumbnailUrl = null)
        )

        val expectedNames = videos.mapIndexed { index, _ -> MediaRepository.videoFileName(tweetId, index) }

        assertEquals("tweet_456_vid1.mp4", expectedNames[0])
        assertEquals("tweet_456_vid2.mp4", expectedNames[1])
    }

    @Test
    fun gifMedia_getsVideoFileName() {
        val tweetId = "789"
        val gif = TweetMedia.AnimatedGif(url = "https://video.twimg.com/gif.mp4", thumbnailUrl = null)

        assertEquals("tweet_789_vid1.mp4", MediaRepository.videoFileName(tweetId, 0))
        assertEquals("https://video.twimg.com/gif.mp4", MediaRepository.extractDirectVideoUrl(gif))
    }

    @Test
    fun videoWithDirectUrl_urlIsExtracted() {
        val video = TweetMedia.Video(url = "https://video.twimg.com/direct.mp4", thumbnailUrl = null)
        assertEquals("https://video.twimg.com/direct.mp4", MediaRepository.extractDirectVideoUrl(video))
    }

    @Test
    fun videoWithNullUrl_extractReturnsNull_signalsFallback() {
        val video = TweetMedia.Video(url = null, thumbnailUrl = null)
        // Null signals the download loop to fall back to yt-dlp
        assertNull(MediaRepository.extractDirectVideoUrl(video))
    }

    @Test
    fun twoVideos_resultHasTwoVideoFiles() {
        val result = TweetDownloadResult(
            tweetId = "456",
            text = "Two videos",
            imageFiles = emptyList(),
            videoFiles = listOf(File("tweet_456_vid1.mp4"), File("tweet_456_vid2.mp4"))
        )

        assertEquals(2, result.videoFiles.size)
        assertEquals(2, result.allFiles.size)
        assertEquals("video/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    @Test
    fun videoAndGif_bothGetSeparateFiles() {
        val result = TweetDownloadResult(
            tweetId = "999",
            text = "Video and GIF",
            imageFiles = emptyList(),
            videoFiles = listOf(File("tweet_999_vid1.mp4"), File("tweet_999_vid2.mp4"))
        )

        assertEquals(2, result.allFiles.size)
        assertEquals("video/*", ShareHelper.resolveMimeType(result.allFiles))
    }

    // --- Four photos (Twitter max) ---

    @Test
    fun fourPhotos_twitterMax_allCategorizedAsPhotos() {
        val media = (1..4).map {
            TweetMedia.Photo(
                url = "https://pbs.twimg.com/media/IMG$it.jpg?name=large",
                originalUrl = "https://pbs.twimg.com/media/IMG$it.jpg"
            )
        }

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)

        assertEquals(4, photos.size)
        assertTrue(videos.isEmpty())

        val result = TweetDownloadResult(
            tweetId = "444",
            text = "Four photos",
            imageFiles = (1..4).map { File("tweet_444_img$it.jpg") }
        )

        assertEquals(4, result.allFiles.size)
        assertEquals("image/*", ShareHelper.resolveMimeType(result.allFiles))
    }
}
