package com.cocode.babakcast.data.repository

import android.util.Log
import com.cocode.babakcast.util.Platform
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.math.roundToInt

/**
 * Wraps all YoutubeDL/yt-dlp interactions used by [MediaRepository].
 */
internal class YoutubeDlWrapper(
    private val tag: String
) {
    private val progressPercentRegex = Regex("([0-9]+(?:\\.[0-9]+)?)%")
    @Volatile private var lastLoggedProgressBucket = -1

    internal fun fetchInfo(request: YoutubeDLRequest): String {
        val output = YoutubeDL.getInstance().execute(request, null)
        return output.out
    }

    internal fun executeDownload(
        request: YoutubeDLRequest,
        onProgress: (Float) -> Unit
    ) {
        lastLoggedProgressBucket = -1
        YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
            val normalized = normalizeProgress(progress, line)
            logProgressIfNeeded(normalized, progress, line)
            onProgress(normalized)
        }
    }

    internal fun extractTranscript(
        url: String,
        transcriptsDir: File,
        language: String
    ): String {
        Log.d(tag, "Starting transcript extraction lang=$language url=$url")
        val startTime = System.currentTimeMillis()
        val request = YoutubeDLRequest(url)
        request.addOption("--skip-download")
        request.addOption("--write-auto-sub")
        request.addOption("--sub-lang", language)
        request.addOption("--sub-format", "vtt")
        request.addOption("-o", File(transcriptsDir, "%(title)s.%(ext)s").absolutePath)

        val output = YoutubeDL.getInstance().execute(request, null)
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
            throw Exception("Transcript not available")
        }

        Log.d(tag, "Transcript extraction complete length=${transcript.length}")
        return transcript
    }

    private fun parseTranscriptFromOutput(output: String): String? {
        val lines = output.lines()
        val transcriptLines = mutableListOf<String>()

        for (line in lines) {
            if (line.matches(Regex("\\d{2}:\\d{2}:\\d{2},\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2},\\d{3}"))) {
                continue
            }
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

    internal companion object {
        internal fun buildInfoRequest(url: String, platform: Platform): YoutubeDLRequest {
            val request = YoutubeDLRequest(url)
            request.addOption("--skip-download")
            request.addOption("--dump-json")
            request.addOption("--no-warnings")
            if (platform == Platform.X) {
                request.addOption("--extractor-args", "twitter:api=syndication")
            }
            return request
        }

        internal fun buildDownloadRequest(url: String, platform: Platform, outputPath: String): YoutubeDLRequest {
            val request = YoutubeDLRequest(url)
            request.addOption("-f", "best[ext=mp4]/best")
            request.addOption("--no-warnings")
            if (platform == Platform.X) {
                request.addOption("--extractor-args", "twitter:api=syndication")
            }
            request.addOption("-o", outputPath)
            return request
        }
    }
}
