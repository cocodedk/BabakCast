package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.domain.video.VideoSplitter
import com.cocode.babakcast.util.Platform
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
 * Supports YouTube and X/Twitter platforms.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "MediaRepository"
    private val videosDir = File(context.getExternalFilesDir(null), "videos")
    private val transcriptsDir = File(context.getExternalFilesDir(null), "transcripts")
    private val progressPercentRegex = Regex("([0-9]+(?:\\.[0-9]+)?)%")
    @Volatile private var lastLoggedProgressBucket = -1

    init {
        videosDir.mkdirs()
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

            val title = YouTubeMetadataParser.extractTitleFromJson(jsonOutput) ?: "Video"
            // X posts don't have chapters
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
     * Extract transcript from YouTube video
     */
    suspend fun extractTranscript(url: String, language: String = "en"): Result<String> = withContext(Dispatchers.IO) {
        try {
            // X/Twitter posts don't have transcripts
            if (XUrlExtractor.isXUrl(url)) {
                return@withContext Result.failure(
                    UnsupportedOperationException("Transcript not available for X/Twitter posts")
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
