package com.cocode.babakcast.domain.audio

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cocode.babakcast.util.DownloadFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioExtractor @Inject constructor() {

    private val tag = "AudioExtractor"

    companion object {
        private const val FILE_NAME_SUFFIX = " - Visit BabakCast"
        private const val AUDIO_EXTENSION = "mp3"
        private const val AUDIO_TAG = "_audio"
        private const val AUDIO_BITRATE = "128k"
        private val videoIdRegex = Regex("(.+)[_-]([A-Za-z0-9_-]{11})$")
        private val bareVideoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
    }

    suspend fun extractAudio(videoFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!videoFile.exists()) {
                Log.e(tag, "extractAudio aborted: source video missing path=${videoFile.absolutePath}")
                return@withContext Result.failure(Exception("Source video not found"))
            }

            val outputDir = videoFile.parentFile
                ?: return@withContext Result.failure(Exception("Invalid output directory"))
            val baseName = DownloadFileParser.stripSuffix(videoFile.nameWithoutExtension)
            val audioBaseName = buildAudioBaseName(baseName)
            val outputFile = File(outputDir, "${appendSuffix(audioBaseName)}.$AUDIO_EXTENSION")

            Log.d(
                tag,
                "extractAudio start source=${videoFile.name} sourceBytes=${videoFile.length()} output=${outputFile.name}"
            )

            val command = "-i \"${videoFile.absolutePath}\" -vn -c:a libmp3lame -b:a $AUDIO_BITRATE -y \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                Log.d(
                    tag,
                    "extractAudio success output=${outputFile.name} outputBytes=${outputFile.length()} bitrate=$AUDIO_BITRATE"
                )
                Result.success(outputFile)
            } else {
                val errorOutput = session.failStackTrace ?: "Unknown error"
                Log.e(tag, "extractAudio failed error=$errorOutput")
                Result.failure(Exception("Audio extraction failed: $errorOutput"))
            }
        } catch (e: Exception) {
            Log.e(tag, "extractAudio exception", e)
            Result.failure(e)
        }
    }

    private fun appendSuffix(baseName: String): String {
        val trimmed = baseName.trim()
        return if (trimmed.endsWith(FILE_NAME_SUFFIX)) trimmed else trimmed + FILE_NAME_SUFFIX
    }

    private fun buildAudioBaseName(baseName: String): String {
        val match = videoIdRegex.find(baseName)
        return when {
            match != null -> {
                val prefix = match.groupValues[1]
                val audioPrefix = if (prefix.endsWith(AUDIO_TAG)) prefix else prefix + AUDIO_TAG
                "${audioPrefix}_${match.groupValues[2]}"
            }
            bareVideoIdRegex.matches(baseName) -> "audio_${baseName}"
            baseName.endsWith(AUDIO_TAG) -> baseName
            else -> baseName + AUDIO_TAG
        }
    }
}
