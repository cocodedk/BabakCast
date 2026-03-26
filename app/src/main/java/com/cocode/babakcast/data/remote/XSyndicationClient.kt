package com.cocode.babakcast.data.remote

import android.util.Log
import com.cocode.babakcast.data.model.TweetMedia
import com.cocode.babakcast.data.model.TweetMediaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the X/Twitter syndication API.
 * Fetches tweet metadata including all media (photos, videos, GIFs)
 * without authentication for public tweets.
 */
@Singleton
class XSyndicationClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val tag = "XSyndicationClient"

    /**
     * Fetch all media details for a tweet.
     * Returns a list of [TweetMedia] entries (photos, videos, animated GIFs).
     */
    suspend fun fetchTweetMedia(tweetId: String): Result<TweetMediaResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&token=x"
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Syndication API returned ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val result = parseMediaDetails(body)
            Log.d(tag, "Fetched ${result.media.size} media items for tweet $tweetId: ${result.media.map { it::class.simpleName }}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch tweet media for $tweetId", e)
            Result.failure(e)
        }
    }

    /**
     * Download an image from a URL to a local file.
     */
    suspend fun downloadImage(imageUrl: String, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Image download failed: HTTP ${response.code}")
                )
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(IOException("Empty image response"))

            outputFile.writeBytes(bytes)
            Log.d(tag, "Downloaded image: ${outputFile.name} (${bytes.size} bytes)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(tag, "Failed to download image: $imageUrl", e)
            Result.failure(e)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parse a syndication API JSON response into a [TweetMediaResult].
         * Exposed for testability.
         */
        fun parseMediaDetails(jsonString: String): TweetMediaResult {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val text = root["text"]?.jsonPrimitive?.content ?: ""
            val mediaDetails = root["mediaDetails"]?.jsonArray
                ?: return TweetMediaResult(text, emptyList())

            val mediaList = mediaDetails.mapNotNull { element ->
                val obj = element.jsonObject
                val type = obj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val mediaUrl = obj["media_url_https"]?.jsonPrimitive?.content

                when (type) {
                    "photo" -> {
                        if (mediaUrl == null) return@mapNotNull null
                        TweetMedia.Photo(url = "${mediaUrl}?name=large", originalUrl = mediaUrl)
                    }
                    "video" -> {
                        val bestVideoUrl = extractBestVideoUrl(obj)
                        TweetMedia.Video(url = bestVideoUrl, thumbnailUrl = mediaUrl)
                    }
                    "animated_gif" -> {
                        val bestVideoUrl = extractBestVideoUrl(obj)
                        TweetMedia.AnimatedGif(url = bestVideoUrl, thumbnailUrl = mediaUrl)
                    }
                    else -> null
                }
            }

            return TweetMediaResult(text, mediaList)
        }

        /**
         * Select the highest-bitrate MP4 variant from a media object's video_info.
         */
        fun extractBestVideoUrl(mediaObj: JsonObject): String? {
            val videoInfo = mediaObj["video_info"]?.jsonObject ?: return null
            val variants = videoInfo["variants"]?.jsonArray ?: return null

            return variants
                .mapNotNull { it.jsonObject }
                .filter { it["content_type"]?.jsonPrimitive?.content == "video/mp4" }
                .maxByOrNull { it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }
                ?.get("url")?.jsonPrimitive?.content
        }
    }
}

typealias TweetMediaResult = com.cocode.babakcast.data.model.TweetMediaResult
typealias TweetMedia = com.cocode.babakcast.data.model.TweetMedia
