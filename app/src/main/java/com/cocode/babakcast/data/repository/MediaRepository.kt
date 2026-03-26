package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.cocode.babakcast.data.model.TweetDownloadResult
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.data.model.TweetMedia
import com.cocode.babakcast.data.remote.XSyndicationClient
import com.cocode.babakcast.domain.video.VideoSplitter
import com.cocode.babakcast.util.Platform
import com.cocode.babakcast.util.YouTubeMetadataParser
import com.cocode.babakcast.util.urlparsing.InstagramUrlExtractor
import com.cocode.babakcast.util.urlparsing.InstagramUrlParser
import com.cocode.babakcast.util.urlparsing.LinkedInUrlExtractor
import com.cocode.babakcast.util.urlparsing.LinkedInUrlParser
import com.cocode.babakcast.util.urlparsing.XUrlExtractor
import com.cocode.babakcast.util.urlparsing.XUrlParser
import com.cocode.babakcast.util.urlparsing.YouTubeUrlParser
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for media operations: download and transcript extraction.
 * Supports YouTube, X/Twitter, Instagram, and LinkedIn platforms.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xSyndicationClient: XSyndicationClient,
    private val okHttpClient: OkHttpClient
) {
    private val tag = "MediaRepository"
    private val videosDir = File(context.getExternalFilesDir(null), "videos")
    private val imagesDir = File(context.getExternalFilesDir(null), "images")
    private val transcriptsDir = File(context.getExternalFilesDir(null), "transcripts")
    private val ytDl = YoutubeDlWrapper(tag)
    private val xDownloader = XDirectDownloader(okHttpClient, tag)

    init {
        videosDir.mkdirs()
        imagesDir.mkdirs()
        transcriptsDir.mkdirs()
        // YoutubeDL is initialized in BabakCastApplication.onCreate() so it's ready before first use
    }

    /**
     * Detect platform and extract media ID in a single pass.
     */
    fun identifyMedia(url: String): MediaIdentifier? {
        YouTubeUrlParser.extractVideoId(url)?.let {
            return MediaIdentifier(Platform.YOUTUBE, it)
        }
        if (XUrlExtractor.isXUrl(url)) {
            val tweetId = XUrlParser.extractTweetId(url) ?: return null
            return MediaIdentifier(Platform.X, tweetId)
        }
        if (InstagramUrlExtractor.isInstagramUrl(url)) {
            val shortcode = InstagramUrlParser.extractShortcode(url) ?: return null
            return MediaIdentifier(Platform.INSTAGRAM, shortcode)
        }
        if (LinkedInUrlExtractor.isLinkedInUrl(url)) {
            val postId = LinkedInUrlParser.extractPostId(url) ?: return null
            return MediaIdentifier(Platform.LINKEDIN, postId)
        }
        return null
    }

    /**
     * Get video info (title, etc.) without downloading
     */
    suspend fun getVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val (platform, mediaId) = identifyMedia(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Unsupported URL"))

            val request = buildInfoRequest(url, platform)
            val jsonOutput = ytDl.fetchInfo(request)

            // For X/Twitter and LinkedIn, prefer the full "description" over the truncated "title".
            // extractChaptersFromJson is YouTube-only; other platforms don't provide chapter metadata.
            val title = if (platform == Platform.X || platform == Platform.LINKEDIN) {
                YouTubeMetadataParser.extractDescriptionFromJson(jsonOutput)
                    ?: YouTubeMetadataParser.extractTitleFromJson(jsonOutput)
                    ?: "Video"
            } else {
                YouTubeMetadataParser.extractTitleFromJson(jsonOutput) ?: "Video"
            }
            val chapters = if (platform == Platform.YOUTUBE) {
                YouTubeMetadataParser.extractChaptersFromJson(jsonOutput)
            } else {
                emptyList()
            }

            val videoInfo = VideoInfo(
                videoId = mediaId,
                title = title,
                url = url,
                chapters = chapters
            )

            Log.d(tag, "getVideoInfo success: platform=$platform mediaId=$mediaId title='$title' chapters=${chapters.size}")

            Result.success(videoInfo)
        } catch (e: Exception) {
            Log.e(tag, "getVideoInfo failed", e)
            Result.failure(e)
        }
    }

    /**
     * Download video
     */
    suspend fun downloadVideo(
        url: String,
        onProgress: (Float) -> Unit
    ): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val (platform, mediaId) = identifyMedia(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Unsupported URL"))

            val metadataResult = getVideoInfo(url)
            val metadata = metadataResult.getOrNull()
            val title = metadata?.title?.trim().orEmpty()
            val chapters = metadata?.chapters.orEmpty()

            val safeTitle = sanitizeFileBaseName(title)
            val baseName = if (safeTitle.isNotBlank()) "${safeTitle}_$mediaId" else mediaId
            val outputFile = File(videosDir, "${baseName}.mp4")

            val request = buildDownloadRequest(url, platform, outputFile.absolutePath)
            ytDl.executeDownload(request, onProgress)

            if (!outputFile.exists()) {
                return@withContext Result.failure(Exception("Download failed: file not created"))
            }

            val fileSize = outputFile.length()
            val needsSplitting = fileSize > VideoSplitter.MAX_CHUNK_SIZE_BYTES

            Log.d(tag, "Download complete: platform=$platform baseName=$baseName path=${outputFile.absolutePath} sizeBytes=$fileSize needsSplitting=$needsSplitting")
            Result.success(
                VideoInfo(
                    videoId = mediaId,
                    title = title.ifBlank {
                        outputFile.nameWithoutExtension.trim()
                    },
                    url = url,
                    chapters = chapters,
                    file = outputFile,
                    fileSizeBytes = fileSize,
                    needsSplitting = needsSplitting
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Download failed", e)
            Result.failure(e)
        }
    }

    suspend fun fetchTweetText(url: String): Result<String> = withContext(Dispatchers.IO) {
        val tweetId = guardTweetTextFetch(url)
            ?: return@withContext Result.failure(IllegalArgumentException("No tweet ID found in URL"))
        xSyndicationClient.fetchTweetMedia(tweetId).map { it.text }
    }

    /**
     * Download all media (photos + videos + GIFs) from an X/Twitter tweet.
     * Uses the syndication API to discover media, then downloads images directly
     * and videos via yt-dlp.
     */
    suspend fun downloadAllXMedia(
        url: String,
        onProgress: (Float) -> Unit
    ): Result<TweetDownloadResult> = withContext(Dispatchers.IO) {
        try {
            val tweetId = XUrlParser.extractTweetId(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot extract tweet ID from URL"))

            Log.d(tag, "downloadAllXMedia: fetching media details for tweet $tweetId")
            onProgress(0.05f)

            val mediaResult = xSyndicationClient.fetchTweetMedia(tweetId).getOrElse { e ->
                return@withContext Result.failure(e)
            }

            if (mediaResult.media.isEmpty()) {
                return@withContext Result.failure(Exception("No media found in tweet"))
            }

            val (photos, rawVideos) = categorizeTweetMedia(mediaResult.media)
            val videos = resolveVideosToDownload(photos, rawVideos)
            if (videos.isEmpty() && rawVideos.isNotEmpty()) {
                Log.d(tag, "downloadAllXMedia: mixed media tweet — skipping ${rawVideos.size} video(s)")
            }

            val totalItems = photos.size + videos.size
            var completedItems = 0

            Log.d(tag, "downloadAllXMedia: ${photos.size} photos, ${videos.size} videos/GIFs")

            // Download photos via direct HTTP
            val imageFiles = mutableListOf<File>()
            for ((index, photo) in photos.withIndex()) {
                val extension = guessImageExtension(photo.originalUrl)
                val outputFile = File(imagesDir, "tweet_${tweetId}_img${index + 1}.$extension")
                xDownloader.downloadPhoto(photo, outputFile).fold(
                    onSuccess = { file ->
                        imageFiles.add(file)
                        completedItems++
                        onProgress(completedItems.toFloat() / totalItems)
                    },
                    onFailure = { e ->
                        Log.w(tag, "Failed to download photo ${index + 1}: ${e.message}")
                        completedItems++
                        onProgress(completedItems.toFloat() / totalItems)
                    }
                )
            }

            // Download videos/GIFs: use the direct MP4 URL from the syndication API when available,
            // fall back to yt-dlp for videos whose URL could not be extracted (e.g. HLS-only).
            val videoFiles = mutableListOf<File>()
            for ((index, videoMedia) in videos.withIndex()) {
                val directUrl = extractDirectVideoUrl(videoMedia)
                val outputFile = File(videosDir, videoFileName(tweetId, index))

                if (directUrl != null) {
                    xDownloader.downloadVideo(directUrl, outputFile).fold(
                        onSuccess = { file ->
                            videoFiles.add(file)
                            completedItems++
                            onProgress(completedItems.toFloat() / totalItems)
                        },
                        onFailure = { e ->
                            Log.w(tag, "Failed to download video ${index + 1}: ${e.message}")
                            completedItems++
                            onProgress(completedItems.toFloat() / totalItems)
                        }
                    )
                } else {
                    // No direct URL — fall back to yt-dlp with the original tweet URL
                    val request = buildDownloadRequest(url, Platform.X, outputFile.absolutePath)
                    ytDl.executeDownload(request) { videoProgress ->
                        val baseProgress = completedItems.toFloat() / totalItems
                        val videoWeight = 1.0f / totalItems
                        onProgress(baseProgress + videoProgress * videoWeight)
                    }
                    if (outputFile.exists()) {
                        videoFiles.add(outputFile)
                    }
                    completedItems++
                    onProgress(completedItems.toFloat() / totalItems)
                }
            }
            if (videos.isNotEmpty()) onProgress(1f)

            if (imageFiles.isEmpty() && videoFiles.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to download any media from tweet"))
            }

            Log.d(tag, "downloadAllXMedia complete: ${imageFiles.size} images, ${videoFiles.size} videos")
            Result.success(
                TweetDownloadResult(
                    tweetId = tweetId,
                    text = mediaResult.text,
                    imageFiles = imageFiles,
                    videoFiles = videoFiles
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "downloadAllXMedia failed", e)
            Result.failure(e)
        }
    }

    // Delegated to companion for testability
    private fun guessImageExtension(url: String): String = Companion.guessImageExtension(url)

    /**
     * Extract transcript from a supported video URL.
     *
     * Returns [Result.success] with the transcript text, or [Result.failure] on error.
     * X/Twitter and Instagram URLs are rejected early with [UnsupportedOperationException]
     * (via [XUrlExtractor.isXUrl] and [InstagramUrlExtractor.isInstagramUrl]) because
     * those platforms do not provide caption data. Currently only YouTube URLs produce
     * transcripts.
     *
     * @param url the video URL to extract a transcript from
     * @param language BCP-47 subtitle language code (default "en")
     * @return transcript text wrapped in a [Result]; runs on [Dispatchers.IO]
     */
    suspend fun extractTranscript(url: String, language: String = "en"): Result<String> = withContext(Dispatchers.IO) {
        try {
            // X/Twitter, Instagram, and LinkedIn posts don't have transcripts
            if (XUrlExtractor.isXUrl(url)) {
                return@withContext Result.failure(
                    UnsupportedOperationException("Transcript not available for X/Twitter posts")
                )
            }
            if (InstagramUrlExtractor.isInstagramUrl(url)) {
                return@withContext Result.failure(
                    UnsupportedOperationException("Transcript not available for Instagram posts")
                )
            }
            if (LinkedInUrlExtractor.isLinkedInUrl(url)) {
                return@withContext Result.failure(
                    UnsupportedOperationException("Transcript not available for LinkedIn posts")
                )
            }
            Result.success(ytDl.extractTranscript(url, transcriptsDir, language))
        } catch (e: Exception) {
            Log.e(tag, "Transcript extraction failed", e)
            Result.failure(e)
        }
    }

    private fun sanitizeFileBaseName(title: String): String {
        if (title.isBlank()) return ""
        return title
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
    }


    /**
     * Clean up video files
     */
    suspend fun cleanupVideos() = withContext(Dispatchers.IO) {
        videosDir.listFiles()?.forEach { it.delete() }
        imagesDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * List downloaded videos
     */
    suspend fun listDownloads(): List<File> = withContext(Dispatchers.IO) {
        videosDir
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?.toList()
            ?: emptyList()
    }

    companion object {
        fun guessImageExtension(url: String): String = XDirectDownloader.guessImageExtension(url)

        /**
         * Returns the tweet ID if [url] is a valid X/Twitter status URL,
         * or null if the URL is not an X URL or has no status path.
         */
        fun guardTweetTextFetch(url: String): String? {
            if (!XUrlExtractor.isXUrl(url)) return null
            return XUrlParser.extractTweetId(url)
        }

        /**
         * Categorize [TweetMedia] items into photos and videos/GIFs.
         * Returns a pair of (photos, videos+GIFs).
         */
        fun categorizeTweetMedia(media: List<TweetMedia>): Pair<List<TweetMedia.Photo>, List<TweetMedia>> {
            val photos = media.filterIsInstance<TweetMedia.Photo>()
            val videos = media.filter { it is TweetMedia.Video || it is TweetMedia.AnimatedGif }
            return photos to videos
        }

        fun videoFileName(tweetId: String, index: Int): String = "tweet_${tweetId}_vid${index + 1}.mp4"

        /**
         * Returns the videos to actually download for a tweet.
         * When a tweet has both photos and videos, videos are skipped: apps like WhatsApp
         * cannot reliably receive mixed image+video FileProvider shares.
         */
        fun resolveVideosToDownload(
            photos: List<TweetMedia.Photo>,
            videos: List<TweetMedia>
        ): List<TweetMedia> = if (photos.isNotEmpty() && videos.isNotEmpty()) emptyList() else videos

        fun extractDirectVideoUrl(media: TweetMedia): String? = when (media) {
            is TweetMedia.Video -> media.url
            is TweetMedia.AnimatedGif -> media.url
            is TweetMedia.Photo -> null
        }

        fun buildInfoRequest(url: String, platform: Platform): YoutubeDLRequest =
            YoutubeDlWrapper.buildInfoRequest(url, platform)

        fun buildDownloadRequest(url: String, platform: Platform, outputPath: String): YoutubeDLRequest =
            YoutubeDlWrapper.buildDownloadRequest(url, platform, outputPath)
    }
}

data class MediaIdentifier(val platform: Platform, val mediaId: String)
