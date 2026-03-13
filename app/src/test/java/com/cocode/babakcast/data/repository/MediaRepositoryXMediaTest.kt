package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.remote.TweetMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the X/Twitter all-media download helpers in [MediaRepository].
 * Covers image extension guessing and media categorization logic.
 */
class MediaRepositoryXMediaTest {

    // --- guessImageExtension ---

    @Test
    fun guessImageExtension_jpgUrl() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123.jpg")
        assertEquals("jpg", ext)
    }

    @Test
    fun guessImageExtension_pngUrl() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123.png")
        assertEquals("png", ext)
    }

    @Test
    fun guessImageExtension_webpUrl() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123.webp")
        assertEquals("webp", ext)
    }

    @Test
    fun guessImageExtension_urlWithQueryParams() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123.png?name=large")
        assertEquals("png", ext)
    }

    @Test
    fun guessImageExtension_unknownExtension_defaultsToJpg() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123.bmp")
        assertEquals("jpg", ext)
    }

    @Test
    fun guessImageExtension_noExtension_defaultsToJpg() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123")
        assertEquals("jpg", ext)
    }

    @Test
    fun guessImageExtension_caseInsensitive_png() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123.PNG")
        assertEquals("png", ext)
    }

    @Test
    fun guessImageExtension_caseInsensitive_webp() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC123.WEBP")
        assertEquals("webp", ext)
    }

    @Test
    fun guessImageExtension_emptyString_defaultsToJpg() {
        assertEquals("jpg", MediaRepository.guessImageExtension(""))
    }

    @Test
    fun guessImageExtension_urlWithFragment_defaultsToJpg() {
        // Fragment is not stripped by substringBefore('?'), so filename becomes "ABC.png#section"
        // which doesn't end with .png → defaults to jpg
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC.png#section")
        assertEquals("jpg", ext)
    }

    @Test
    fun guessImageExtension_urlWithQueryThenFragment() {
        // '?' comes before '#', so substringBefore('?') strips both
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC.png?name=large#section")
        assertEquals("png", ext)
    }

    @Test
    fun guessImageExtension_urlWithMultipleQueryParams() {
        val ext = MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC.webp?name=large&format=webp")
        assertEquals("webp", ext)
    }

    @Test
    fun guessImageExtension_justFilename() {
        assertEquals("png", MediaRepository.guessImageExtension("ABC123.png"))
    }

    @Test
    fun guessImageExtension_pathEndingInSlash_defaultsToJpg() {
        assertEquals("jpg", MediaRepository.guessImageExtension("https://pbs.twimg.com/media/"))
    }

    @Test
    fun guessImageExtension_gifExtension_defaultsToJpg() {
        // .gif is not a recognized image extension for download (GIFs are MP4 on Twitter)
        assertEquals("jpg", MediaRepository.guessImageExtension("https://pbs.twimg.com/media/ABC.gif"))
    }

    // --- categorizeTweetMedia ---

    @Test
    fun categorizeTweetMedia_photosOnly() {
        val media = listOf(
            TweetMedia.Photo(url = "https://img1.jpg?name=large", originalUrl = "https://img1.jpg"),
            TweetMedia.Photo(url = "https://img2.jpg?name=large", originalUrl = "https://img2.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)

        assertEquals(2, photos.size)
        assertTrue(videos.isEmpty())
    }

    @Test
    fun categorizeTweetMedia_videosOnly() {
        val media = listOf(
            TweetMedia.Video(url = "https://video.mp4", thumbnailUrl = "https://thumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)

        assertTrue(photos.isEmpty())
        assertEquals(1, videos.size)
    }

    @Test
    fun categorizeTweetMedia_gifsOnly() {
        val media = listOf(
            TweetMedia.AnimatedGif(url = "https://gif.mp4", thumbnailUrl = "https://thumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)

        assertTrue(photos.isEmpty())
        assertEquals(1, videos.size)
    }

    @Test
    fun categorizeTweetMedia_mixed() {
        val media = listOf(
            TweetMedia.Photo(url = "https://img.jpg?name=large", originalUrl = "https://img.jpg"),
            TweetMedia.Video(url = "https://video.mp4", thumbnailUrl = "https://thumb.jpg"),
            TweetMedia.AnimatedGif(url = "https://gif.mp4", thumbnailUrl = "https://gifthumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)

        assertEquals(1, photos.size)
        assertEquals(2, videos.size)
        assertTrue(videos[0] is TweetMedia.Video)
        assertTrue(videos[1] is TweetMedia.AnimatedGif)
    }

    @Test
    fun categorizeTweetMedia_emptyList() {
        val (photos, videos) = MediaRepository.categorizeTweetMedia(emptyList())

        assertTrue(photos.isEmpty())
        assertTrue(videos.isEmpty())
    }

    @Test
    fun categorizeTweetMedia_preservesPhotoOrder() {
        val media = listOf(
            TweetMedia.Photo(url = "https://first.jpg?name=large", originalUrl = "https://first.jpg"),
            TweetMedia.Photo(url = "https://second.jpg?name=large", originalUrl = "https://second.jpg"),
            TweetMedia.Photo(url = "https://third.jpg?name=large", originalUrl = "https://third.jpg")
        )

        val (photos, _) = MediaRepository.categorizeTweetMedia(media)

        assertEquals("https://first.jpg", photos[0].originalUrl)
        assertEquals("https://second.jpg", photos[1].originalUrl)
        assertEquals("https://third.jpg", photos[2].originalUrl)
    }

    @Test
    fun categorizeTweetMedia_preservesVideoOrder() {
        val media = listOf(
            TweetMedia.Video(url = "https://vid1.mp4", thumbnailUrl = null),
            TweetMedia.AnimatedGif(url = "https://gif1.mp4", thumbnailUrl = null),
            TweetMedia.Video(url = "https://vid2.mp4", thumbnailUrl = null)
        )

        val (_, videos) = MediaRepository.categorizeTweetMedia(media)

        assertEquals(3, videos.size)
        assertEquals("https://vid1.mp4", (videos[0] as TweetMedia.Video).url)
        assertEquals("https://gif1.mp4", (videos[1] as TweetMedia.AnimatedGif).url)
        assertEquals("https://vid2.mp4", (videos[2] as TweetMedia.Video).url)
    }

    @Test
    fun categorizeTweetMedia_photoAndGif() {
        val media = listOf(
            TweetMedia.Photo(url = "https://img.jpg?name=large", originalUrl = "https://img.jpg"),
            TweetMedia.AnimatedGif(url = "https://gif.mp4", thumbnailUrl = null)
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)

        assertEquals(1, photos.size)
        assertEquals(1, videos.size)
        assertTrue(videos[0] is TweetMedia.AnimatedGif)
    }

    @Test
    fun categorizeTweetMedia_multiplePhotosAndOneVideo() {
        val media = listOf(
            TweetMedia.Photo(url = "https://img1.jpg?name=large", originalUrl = "https://img1.jpg"),
            TweetMedia.Photo(url = "https://img2.jpg?name=large", originalUrl = "https://img2.jpg"),
            TweetMedia.Photo(url = "https://img3.jpg?name=large", originalUrl = "https://img3.jpg"),
            TweetMedia.Video(url = "https://video.mp4", thumbnailUrl = "https://thumb.jpg")
        )

        val (photos, videos) = MediaRepository.categorizeTweetMedia(media)

        assertEquals(3, photos.size)
        assertEquals(1, videos.size)
    }
}
