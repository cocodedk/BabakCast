package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.TweetMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [XSyndicationClient.parseMediaDetails] and [XSyndicationClient.extractBestVideoUrl].
 * Exercises the production parsing code directly without network calls.
 */
class XSyndicationClientParseTest {

    @Test
    fun parseSinglePhoto() {
        val response = """
        {
            "text": "Check out this photo!",
            "mediaDetails": [
                {
                    "type": "photo",
                    "media_url_https": "https://pbs.twimg.com/media/ABC123.jpg"
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals("Check out this photo!", result.text)
        assertEquals(1, result.media.size)
        val photo = result.media[0] as TweetMedia.Photo
        assertEquals("https://pbs.twimg.com/media/ABC123.jpg?name=large", photo.url)
        assertEquals("https://pbs.twimg.com/media/ABC123.jpg", photo.originalUrl)
    }

    @Test
    fun parseMultiplePhotos() {
        val response = """
        {
            "text": "Photo thread",
            "mediaDetails": [
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG1.jpg"},
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG2.png"},
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG3.jpg"},
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG4.webp"}
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(4, result.media.size)
        assertTrue(result.media.all { it is TweetMedia.Photo })
    }

    @Test
    fun parseSingleVideo() {
        val response = """
        {
            "text": "Watch this video",
            "mediaDetails": [
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/ext_tw_video_thumb/123/pu/img/thumb.jpg",
                    "video_info": {
                        "aspect_ratio": [16, 9],
                        "duration_millis": 30000,
                        "variants": [
                            {"bitrate": "256000", "content_type": "video/mp4", "url": "https://video.twimg.com/ext_tw_video/123/pu/vid/480x270/low.mp4"},
                            {"bitrate": "2176000", "content_type": "video/mp4", "url": "https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/high.mp4"},
                            {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/ext_tw_video/123/pu/pl/master.m3u8"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(1, result.media.size)
        val video = result.media[0] as TweetMedia.Video
        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/high.mp4", video.url)
        assertEquals("https://pbs.twimg.com/ext_tw_video_thumb/123/pu/img/thumb.jpg", video.thumbnailUrl)
    }

    @Test
    fun parseAnimatedGif() {
        val response = """
        {
            "text": "Funny GIF",
            "mediaDetails": [
                {
                    "type": "animated_gif",
                    "media_url_https": "https://pbs.twimg.com/tweet_video_thumb/GIF123.jpg",
                    "video_info": {
                        "aspect_ratio": [1, 1],
                        "variants": [
                            {"bitrate": "0", "content_type": "video/mp4", "url": "https://video.twimg.com/tweet_video/GIF123.mp4"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(1, result.media.size)
        val gif = result.media[0] as TweetMedia.AnimatedGif
        assertEquals("https://video.twimg.com/tweet_video/GIF123.mp4", gif.url)
    }

    @Test
    fun parseNoMediaDetails() {
        val response = """{"text": "Just a text tweet"}"""

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals("Just a text tweet", result.text)
        assertTrue(result.media.isEmpty())
    }

    @Test
    fun parseEmptyMediaDetails() {
        val response = """{"text": "Nothing here", "mediaDetails": []}"""

        val result = XSyndicationClient.parseMediaDetails(response)

        assertTrue(result.media.isEmpty())
    }

    @Test
    fun parseUnknownMediaTypeIsSkipped() {
        val response = """
        {
            "text": "Unknown media",
            "mediaDetails": [
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG.jpg"},
                {"type": "poll", "media_url_https": "https://example.com/poll"}
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(1, result.media.size)
        assertTrue(result.media[0] is TweetMedia.Photo)
    }

    @Test
    fun parsePhotoWithNullMediaUrl_isSkipped() {
        val response = """
        {
            "text": "Null URL photo",
            "mediaDetails": [
                {"type": "photo"},
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/VALID.jpg"}
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(1, result.media.size)
        val photo = result.media[0] as TweetMedia.Photo
        assertEquals("https://pbs.twimg.com/media/VALID.jpg", photo.originalUrl)
    }

    @Test
    fun parseVideoWithNoVideoInfo_urlIsNull() {
        val response = """
        {
            "text": "Video without video_info",
            "mediaDetails": [
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg"
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(1, result.media.size)
        val video = result.media[0] as TweetMedia.Video
        assertNull(video.url)
        assertEquals("https://pbs.twimg.com/thumb.jpg", video.thumbnailUrl)
    }

    @Test
    fun parseVideoWithOnlyHlsVariants_urlIsNull() {
        val response = """
        {
            "text": "HLS only video",
            "mediaDetails": [
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg",
                    "video_info": {
                        "variants": [
                            {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/master.m3u8"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        val video = result.media[0] as TweetMedia.Video
        assertNull(video.url)
    }

    @Test
    fun parseMixedPhotoAndVideo() {
        val response = """
        {
            "text": "Photo and video together",
            "mediaDetails": [
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG.jpg"},
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg",
                    "video_info": {
                        "variants": [
                            {"bitrate": "2176000", "content_type": "video/mp4", "url": "https://video.twimg.com/vid.mp4"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(2, result.media.size)
        assertTrue(result.media[0] is TweetMedia.Photo)
        assertTrue(result.media[1] is TweetMedia.Video)
    }

    @Test
    fun parseMediaEntryMissingType_isSkipped() {
        val response = """
        {
            "text": "Missing type",
            "mediaDetails": [
                {"media_url_https": "https://pbs.twimg.com/media/IMG.jpg"},
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/VALID.jpg"}
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(1, result.media.size)
        assertTrue(result.media[0] is TweetMedia.Photo)
    }

    @Test
    fun parseRootMissingTextField_defaultsToEmpty() {
        val response = """
        {
            "mediaDetails": [
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG.jpg"}
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals("", result.text)
        assertEquals(1, result.media.size)
    }

    @Test
    fun parseVideoWithNullMediaUrl_thumbnailIsNull() {
        val response = """
        {
            "text": "Video no thumb",
            "mediaDetails": [
                {
                    "type": "video",
                    "video_info": {
                        "variants": [
                            {"bitrate": "2176000", "content_type": "video/mp4", "url": "https://video.twimg.com/vid.mp4"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        val video = result.media[0] as TweetMedia.Video
        assertEquals("https://video.twimg.com/vid.mp4", video.url)
        assertNull(video.thumbnailUrl)
    }

    @Test
    fun parseAnimatedGifWithNullMediaUrl_thumbnailIsNull() {
        val response = """
        {
            "text": "GIF no thumb",
            "mediaDetails": [
                {
                    "type": "animated_gif",
                    "video_info": {
                        "variants": [
                            {"bitrate": "0", "content_type": "video/mp4", "url": "https://video.twimg.com/gif.mp4"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        val gif = result.media[0] as TweetMedia.AnimatedGif
        assertEquals("https://video.twimg.com/gif.mp4", gif.url)
        assertNull(gif.thumbnailUrl)
    }

    @Test
    fun parseVideoWithEmptyVariants_urlIsNull() {
        val response = """
        {
            "text": "Empty variants",
            "mediaDetails": [
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg",
                    "video_info": {
                        "variants": []
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        val video = result.media[0] as TweetMedia.Video
        assertNull(video.url)
    }

    @Test
    fun parseVideoWithMissingVariantsField_urlIsNull() {
        val response = """
        {
            "text": "No variants",
            "mediaDetails": [
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg",
                    "video_info": {}
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        val video = result.media[0] as TweetMedia.Video
        assertNull(video.url)
    }

    @Test
    fun parseMixedPhotoVideoGif_allThreeTypes() {
        val response = """
        {
            "text": "All three types",
            "mediaDetails": [
                {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/IMG.jpg"},
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/vthumb.jpg",
                    "video_info": {
                        "variants": [{"bitrate": "1000000", "content_type": "video/mp4", "url": "https://video.twimg.com/vid.mp4"}]
                    }
                },
                {
                    "type": "animated_gif",
                    "media_url_https": "https://pbs.twimg.com/gthumb.jpg",
                    "video_info": {
                        "variants": [{"bitrate": "0", "content_type": "video/mp4", "url": "https://video.twimg.com/gif.mp4"}]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(3, result.media.size)
        assertTrue(result.media[0] is TweetMedia.Photo)
        assertTrue(result.media[1] is TweetMedia.Video)
        assertTrue(result.media[2] is TweetMedia.AnimatedGif)
    }

    @Test
    fun videoSelectsHighestBitrate() {
        val response = """
        {
            "text": "Video",
            "mediaDetails": [
                {
                    "type": "video",
                    "media_url_https": "https://pbs.twimg.com/thumb.jpg",
                    "video_info": {
                        "variants": [
                            {"bitrate": "832000", "content_type": "video/mp4", "url": "https://video.twimg.com/medium.mp4"},
                            {"bitrate": "2176000", "content_type": "video/mp4", "url": "https://video.twimg.com/high.mp4"},
                            {"bitrate": "256000", "content_type": "video/mp4", "url": "https://video.twimg.com/low.mp4"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)
        val video = result.media[0] as TweetMedia.Video

        assertEquals("https://video.twimg.com/high.mp4", video.url)
    }

    @Test
    fun parseTwoPhotoTweet_exactlyTwoPhotosWithNameLargeUrls() {
        // Matches the JSON structure returned by the syndication API for
        // https://x.com/hey_itsmyturn/status/2037106757810393340
        val response = """
        {
            "text": "Check out these two photos!",
            "mediaDetails": [
                {
                    "type": "photo",
                    "media_url_https": "https://pbs.twimg.com/media/HEVBvNbXAAAXz99.jpg"
                },
                {
                    "type": "photo",
                    "media_url_https": "https://pbs.twimg.com/media/HEVBvyNboAAc3Hk.jpg"
                }
            ]
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals("Check out these two photos!", result.text)
        assertEquals(2, result.media.size)
        assertTrue(result.media.all { it is TweetMedia.Photo })

        val photo1 = result.media[0] as TweetMedia.Photo
        assertEquals("https://pbs.twimg.com/media/HEVBvNbXAAAXz99.jpg?name=large", photo1.url)
        assertEquals("https://pbs.twimg.com/media/HEVBvNbXAAAXz99.jpg", photo1.originalUrl)

        val photo2 = result.media[1] as TweetMedia.Photo
        assertEquals("https://pbs.twimg.com/media/HEVBvyNboAAc3Hk.jpg?name=large", photo2.url)
        assertEquals("https://pbs.twimg.com/media/HEVBvyNboAAc3Hk.jpg", photo2.originalUrl)
    }

    @Test
    fun parseTwoPhotoTweet_categorizedAsTwoPhotosZeroVideos() {
        // Verify that parseMediaDetails + categorizeTweetMedia return 2 photos and 0 videos.
        // This integration path covers the full pipeline before any HTTP downloads.
        val response = """
        {
            "text": "Two photos",
            "mediaDetails": [
                {
                    "type": "photo",
                    "media_url_https": "https://pbs.twimg.com/media/HEVBvNbXAAAXz99.jpg"
                },
                {
                    "type": "photo",
                    "media_url_https": "https://pbs.twimg.com/media/HEVBvyNboAAc3Hk.jpg"
                }
            ]
        }
        """.trimIndent()

        val parsed = XSyndicationClient.parseMediaDetails(response)
        val (photos, videos) = com.cocode.babakcast.data.repository.MediaRepository.categorizeTweetMedia(parsed.media)

        assertEquals(2, photos.size)
        assertTrue(videos.isEmpty())
        assertEquals("https://pbs.twimg.com/media/HEVBvNbXAAAXz99.jpg", photos[0].originalUrl)
        assertEquals("https://pbs.twimg.com/media/HEVBvyNboAAc3Hk.jpg", photos[1].originalUrl)
    }

    @Test
    fun parseNoteTweetText_returnsFullTextOverTruncatedText() {
        val response = """
        {
            "text": "First 280 chars truncated...",
            "note_tweet": {
                "text": "Full long-form text that exceeds 280 characters and is only available via note_tweet field for X Premium posts."
            },
            "mediaDetails": []
        }
        """.trimIndent()

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals(
            "Full long-form text that exceeds 280 characters and is only available via note_tweet field for X Premium posts.",
            result.text
        )
    }

    @Test
    fun parseNoNoteTweetField_fallsBackToTextField() {
        val response = """{"text": "Regular short tweet"}"""

        val result = XSyndicationClient.parseMediaDetails(response)

        assertEquals("Regular short tweet", result.text)
    }
}
