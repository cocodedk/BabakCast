package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.cocode.babakcast.data.model.VideoInfo
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
 * Repository for YouTube operations: download and transcript extraction
 */
@Singleton
class YouTubeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FILE_NAME_SUFFIX = " - Visit BabakCast"
    }

    private val tag = "YouTubeRepository"
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
     * Extract video ID from YouTube URL
     */
    fun extractVideoId(url: String): String? {
        return YouTubeUrlParser.extractVideoId(url)
    }

    /**
     * Get video info (title, etc.) without downloading
     */
    suspend fun getVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL"))

            val request = YoutubeDLRequest(url)
            request.addOption("--skip-download")
            request.addOption("--dump-json")

            val output = YoutubeDL.getInstance().execute(request, null)
            val jsonOutput = output.out

            // Parse title from JSON (simplified - youtubedl-android may return JSON)
            val title = extractTitleFromJson(jsonOutput) ?: "Video"

            Result.success(
                VideoInfo(
                    videoId = videoId,
                    title = title,
                    url = url
                )
            )
        } catch (e: Exception) {
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
            val videoId = extractVideoId(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL"))

            val title = getVideoInfo(url).getOrNull()?.title?.trim().orEmpty()
            val safeTitle = sanitizeFileBaseName(title)
            val baseName = if (safeTitle.isNotBlank()) "${safeTitle}_$videoId" else videoId
            val outputFile = File(videosDir, "${appendSuffix(baseName)}.mp4")
            
            lastLoggedProgressBucket = -1
            Log.d(tag, "Starting download for videoId=$videoId")
            val request = YoutubeDLRequest(url)
            request.addOption("-f", "best[ext=mp4]/best")
            request.addOption("-o", outputFile.absolutePath)

            YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                val normalized = normalizeProgress(progress, line)
                logProgressIfNeeded(normalized, progress, line)
                onProgress(normalized)
            }

            if (!outputFile.exists()) {
                Log.e(tag, "Download failed: file not created for videoId=$videoId")
                return@withContext Result.failure(Exception("Download failed: file not created"))
            }

            val fileSize = outputFile.length()
            val needsSplitting = fileSize > 16 * 1024 * 1024 // 16 MB limit

            Log.d(tag, "Download complete for videoId=$videoId sizeBytes=$fileSize needsSplitting=$needsSplitting")
            Result.success(
                VideoInfo(
                    videoId = videoId,
                    title = title.ifBlank { outputFile.nameWithoutExtension },
                    url = url,
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
     * Extract title from JSON output (simplified)
     */
    private fun extractTitleFromJson(jsonOutput: String): String? {
        // Simple JSON parsing - in production, use proper JSON parser
        val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(jsonOutput)
        return titleMatch?.groupValues?.get(1)
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

    private fun appendSuffix(baseName: String): String {
        val trimmed = baseName.trim()
        return if (trimmed.endsWith(FILE_NAME_SUFFIX)) trimmed else trimmed + FILE_NAME_SUFFIX
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
}
