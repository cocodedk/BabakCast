package com.cocode.babakcast.data.repository

import android.content.Context
import com.cocode.babakcast.data.model.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for YouTube operations: download and transcript extraction
 */
@Singleton
class YouTubeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val videosDir = File(context.getExternalFilesDir(null), "videos")
    
    init {
        videosDir.mkdirs()
        // Initialize YouTubeDL
        try {
            YoutubeDL.getInstance().init(context)
        } catch (e: Exception) {
            // Already initialized or error
        }
    }

    /**
     * Extract video ID from YouTube URL
     */
    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "(?:youtube\\.com\\/watch\\?v=|youtu\\.be\\/|youtube\\.com\\/embed\\/)([^&\\n?#]+)",
            "youtube\\.com\\/watch\\?.*v=([^&\\n?#]+)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
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

            val outputFile = File(videosDir, "$videoId.mp4")
            
            val request = YoutubeDLRequest(url)
            request.addOption("-f", "best[ext=mp4]/best")
            request.addOption("-o", outputFile.absolutePath)

            YoutubeDL.getInstance().execute(request, null) { progress, _, _ ->
                onProgress(progress)
            }

            if (!outputFile.exists()) {
                return@withContext Result.failure(Exception("Download failed: file not created"))
            }

            val fileSize = outputFile.length()
            val needsSplitting = fileSize > 16 * 1024 * 1024 // 16 MB

            Result.success(
                VideoInfo(
                    videoId = videoId,
                    title = outputFile.nameWithoutExtension,
                    url = url,
                    file = if (!needsSplitting) outputFile else null,
                    fileSizeBytes = fileSize,
                    needsSplitting = needsSplitting
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract transcript from YouTube video
     */
    suspend fun extractTranscript(url: String, language: String = "en"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--skip-download")
            request.addOption("--write-auto-sub")
            request.addOption("--sub-lang", language)
            request.addOption("--sub-format", "vtt")
            request.addOption("--convert-subs", "srt")
            request.addOption("-o", "%(title)s.%(ext)s")

            val output = YoutubeDL.getInstance().execute(request, null)
            
            // Parse transcript from output or subtitle file
            // Note: youtubedl-android may handle this differently
            // This is a simplified implementation
            val transcript = parseTranscriptFromOutput(output.out)
                ?: return@withContext Result.failure(Exception("Transcript not available"))

            Result.success(transcript)
        } catch (e: Exception) {
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

    /**
     * Clean up video files
     */
    suspend fun cleanupVideos() = withContext(Dispatchers.IO) {
        videosDir.listFiles()?.forEach { it.delete() }
    }
}
