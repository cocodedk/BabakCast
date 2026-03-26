package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.cocode.babakcast.data.model.TweetDownloadResult
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.data.remote.TweetMedia
import com.cocode.babakcast.data.remote.XSyndicationClient
import com.cocode.babakcast.domain.video.VideoSplitter
import com.cocode.babakcast.util.Platform
import com.cocode.babakcast.util.InstagramUrlExtractor
import com.cocode.babakcast.util.InstagramUrlParser
import com.cocode.babakcast.util.LinkedInUrlExtractor
import com.cocode.babakcast.util.LinkedInUrlParser
import com.cocode.babakcast.util.XUrlExtractor
import com.cocode.babakcast.util.XUrlParser
import com.cocode.babakcast.util.YouTubeMetadataParser
import com.cocode.babakcast.util.YouTubeUrlParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Repository for media operations: download and transcript extraction.
 * Supports YouTube, X/Twitter, Instagram, and LinkedIn platforms.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xSyndicationClient: XSyndicationClient
) {
    private val tag = "MediaRepository"
    private val videosDir = File(context.getExternalFilesDir(null), "videos")
    private val imagesDir = File(context.getExternalFilesDir(null), "images")
    private val transcriptsDir = File(context.getExternalFilesDir(null), "transcripts")
    private val progressPercentRegex = Regex("([0-9]+(?:\\.[0-9]+)?)%")
    @Volatile private var lastLoggedProgressBucket = -1

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

            val output = YoutubeDL.getInstance().execute(request, null)
            val jsonOutput = output.out

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

            lastLoggedProgressBucket = -1
            val request = buildDownloadRequest(url, platform, outputFile.absolutePath)

            YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                val normalized = normalizeProgress(progress, line)
                logProgressIfNeeded(normalized, progress, line)
                onProgress(normalized)
            }

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

            val (photos, videos) = categorizeTweetMedia(mediaResult.media)

            val totalItems = photos.size + videos.size
            var completedItems = 0

            Log.d(tag, "downloadAllXMedia: ${photos.size} photos, ${videos.size} videos/GIFs")

            // Download photos via direct HTTP
            val imageFiles = mutableListOf<File>()
            for ((index, photo) in photos.withIndex()) {
                val extension = guessImageExtension(photo.originalUrl)
                val outputFile = File(imagesDir, "tweet_${tweetId}_img${index + 1}.$extension")
                xSyndicationClient.downloadImage(photo.url, outputFile).fold(
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

            // Download videos/GIFs via yt-dlp
            val videoFiles = mutableListOf<File>()
            if (videos.isNotEmpty()) {
                val safeTitle = sanitizeFileBaseName(mediaResult.text)
                val baseName = if (safeTitle.isNotBlank()) "${safeTitle}_$tweetId" else tweetId
                val outputFile = File(videosDir, "${baseName}.mp4")

                lastLoggedProgressBucket = -1
                val request = buildDownloadRequest(url, Platform.X, outputFile.absolutePath)

                YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                    val videoProgress = normalizeProgress(progress, line)
                    val baseProgress = completedItems.toFloat() / totalItems
                    val videoWeight = videos.size.toFloat() / totalItems
                    onProgress(baseProgress + videoProgress * videoWeight)
                }

                if (outputFile.exists()) {
                    videoFiles.add(outputFile)
                }
                completedItems += videos.size
                onProgress(1f)
            }

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
            Log.d(tag, "Starting transcript extraction lang=$language url=$url")
            val startTime = System.currentTimeMillis()
            val request = YoutubeDLRequest(url)
            request.addOption("--skip-download")
            request.addOption("--write-auto-sub")
            request.addOption("--sub-lang", language)
            request.addOption("--sub-format", "vtt")
            request.addOption("-o", File(transcriptsDir, "%(title)s.%(ext)s").absolutePath)

            val output = YoutubeDL.getInstance().execute(request, null)
            
            // Parse transcript from output or subtitle file
            // Note: youtubedl-android may handle this differently
            // This is a simplified implementation
            val newVtt = transcriptsDir
                .listFiles()
                ?.filter { it.isFile && it.extension.equals("vtt", ignoreCase = true) }
                ?.filter { it.lastModified() >= startTime - 1000 }
                ?.maxByOrNull { it.lastModified() }

            val transcript = when {
                newVtt != null -> parseTranscriptFromVtt(newVtt)
                else -> parseTranscriptFromOutput(output.out)
                    ?.takeUnless { looksLikeYtdlpLog(it) }
            } ?: run {
                Log.e(tag, "Transcript not available. Output snippet=${output.out.take(400)}")
                return@withContext Result.failure(Exception("Transcript not available"))
            }

            Log.d(tag, "Transcript extraction complete length=${transcript.length}")
            Result.success(transcript)
        } catch (e: Exception) {
            Log.e(tag, "Transcript extraction failed", e)
            Result.failure(e)
        }
    }

    /**
     * Parse transcript from output (simplified - actual implementation depends on youtubedl-android output format)
     */
    private fun parseTranscriptFromOutput(output: String): String? {
        // Remove timestamps and format
        // Format: [00:00:00.000 --> 00:00:05.000] Text
        val lines = output.lines()
        val transcriptLines = mutableListOf<String>()
        
        for (line in lines) {
            // Skip timestamp lines
            if (line.matches(Regex("\\d{2}:\\d{2}:\\d{2},\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2},\\d{3}"))) {
                continue
            }
            // Skip empty lines and sequence numbers
            if (line.isBlank() || line.matches(Regex("^\\d+$"))) {
                continue
            }
            transcriptLines.add(line.trim())
        }
        
        return transcriptLines.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun parseTranscriptFromVtt(file: File): String? {
        val lines = file.readLines()
        val transcriptLines = mutableListOf<String>()
        val timestampRegex = Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.equals("WEBVTT", ignoreCase = true)) continue
            if (trimmed.startsWith("NOTE", ignoreCase = true)) continue
            if (trimmed.matches(timestampRegex)) continue
            if (trimmed.matches(Regex("^\\d+$"))) continue

            val cleaned = trimmed.replace(Regex("<[^>]+>"), "").trim()
            if (cleaned.isNotBlank()) {
                transcriptLines.add(cleaned)
            }
        }

        return transcriptLines.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun looksLikeYtdlpLog(text: String): Boolean {
        val lowered = text.lowercase()
        return lowered.contains("[youtube]") ||
            lowered.contains("downloading") ||
            lowered.contains("extracting url") ||
            lowered.contains("writing video subtitles")
    }

    private fun normalizeProgress(progress: Float, line: String?): Float {
        val percentFromLine = line?.let {
            progressPercentRegex.find(it)?.groupValues?.get(1)?.toFloatOrNull()
        }
        val raw = percentFromLine ?: progress
        if (!raw.isFinite()) return 0f
        if (raw <= 0f) return 0f
        val normalized = if (percentFromLine != null) {
            raw / 100f
        } else if (raw > 1f) {
            raw / 100f
        } else {
            raw
        }
        return normalized.coerceIn(0f, 1f)
    }

    private fun logProgressIfNeeded(normalized: Float, raw: Float, line: String?) {
        val percentInt = (normalized * 100).roundToInt().coerceIn(0, 100)
        val bucket = percentInt / 10
        if (bucket != lastLoggedProgressBucket) {
            lastLoggedProgressBucket = bucket
            val snippet = line?.replace(Regex("\\s+"), " ")?.take(200)
            Log.d(tag, "Download progress: $percentInt% (raw=$raw line=${snippet ?: "n/a"})")
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
        fun guessImageExtension(url: String): String {
            val path = url.substringBefore('?').substringAfterLast('/')
            return when {
                path.endsWith(".png", ignoreCase = true) -> "png"
                path.endsWith(".webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
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

        fun buildInfoRequest(url: String, platform: Platform): YoutubeDLRequest {
            val request = YoutubeDLRequest(url)
            request.addOption("--skip-download")
            request.addOption("--dump-json")
            request.addOption("--no-warnings")
            if (platform == Platform.X) {
                request.addOption("--extractor-args", "twitter:api=syndication")
            }
            return request
        }

        fun buildDownloadRequest(url: String, platform: Platform, outputPath: String): YoutubeDLRequest {
            val request = YoutubeDLRequest(url)
            // Single-stream format to avoid ffmpeg merging
            request.addOption("-f", "best[ext=mp4]/best")
            request.addOption("--no-warnings")
            if (platform == Platform.X) {
                // Use syndication API — no authentication required for public tweets
                request.addOption("--extractor-args", "twitter:api=syndication")
            }
            request.addOption("-o", outputPath)
            return request
        }
    }
}

data class MediaIdentifier(val platform: Platform, val mediaId: String)
