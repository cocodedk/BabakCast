package com.cocode.babakcast.domain.audio

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioSplitter @Inject constructor() {

    private val tag = "AudioSplitter"

    companion object {
        private const val MAX_CHUNK_SIZE_BYTES = 16 * 1024 * 1024
        private const val TARGET_CHUNK_SIZE_BYTES = 15 * 1024 * 1024
        private const val MAX_SPLIT_ATTEMPTS = 5
        private const val FILE_NAME_SUFFIX = " - Visit BabakCast"
        private const val DEFAULT_EXTENSION = "mp3"
    }

    suspend fun splitAudioIfNeeded(
        audioFile: File,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)? = null
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists()) {
                Log.e(tag, "splitAudioIfNeeded aborted: source file missing path=${audioFile.absolutePath}")
                return@withContext Result.failure(Exception("Audio file not found"))
            }

            val sourceSize = audioFile.length()
            Log.d(
                tag,
                "splitAudioIfNeeded start name=${audioFile.name} sizeBytes=$sourceSize maxChunkBytes=$MAX_CHUNK_SIZE_BYTES"
            )

            if (sourceSize <= MAX_CHUNK_SIZE_BYTES) {
                Log.d(tag, "splitAudioIfNeeded skip: size within limit, returning original file")
                return@withContext Result.success(listOf(audioFile))
            }

            val duration = getMediaDuration(audioFile)
                ?: return@withContext Result.failure(Exception("Could not determine audio duration"))

            if (duration <= 0.0) {
                return@withContext Result.failure(Exception("Invalid audio duration"))
            }

            val bytesPerSecond = audioFile.length() / duration
            if (bytesPerSecond <= 0.0) {
                return@withContext Result.failure(Exception("Invalid bitrate estimate"))
            }

            val chunkDuration = TARGET_CHUNK_SIZE_BYTES.toDouble() / bytesPerSecond
            val outputDir = audioFile.parentFile
                ?: return@withContext Result.failure(Exception("Invalid output directory"))
            val baseName = stripSuffix(audioFile.nameWithoutExtension)
            val outputExtension = audioFile.extension.ifBlank { DEFAULT_EXTENSION }
            val splitFiles = mutableListOf<File>()

            val estimatedParts = kotlin.math.ceil(duration / chunkDuration).toInt().coerceAtLeast(1)
            val indexWidth = estimatedParts.toString().length

            Log.d(
                tag,
                "splitAudioIfNeeded planning durationSec=${formatSeconds(duration)} bytesPerSec=${"%.2f".format(java.util.Locale.US, bytesPerSecond)} targetChunkSec=${formatSeconds(chunkDuration)} estimatedParts=$estimatedParts"
            )

            var currentTime = 0.0
            var chunkIndex = 0

            while (currentTime < duration) {
                onProgress?.invoke(chunkIndex + 1, estimatedParts)
                val partNumber = (chunkIndex + 1).toString().padStart(indexWidth, '0')
                val outputBaseName = "${baseName}_part${partNumber}"
                val outputFile = File(outputDir, "${appendSuffix(outputBaseName)}.$outputExtension")

                var segmentDuration = minOf(chunkDuration, duration - currentTime)
                var attempt = 0
                var splitSuccess = false

                while (attempt < MAX_SPLIT_ATTEMPTS && !splitSuccess) {
                    Log.d(
                        tag,
                        "splitAudioIfNeeded chunk=${chunkIndex + 1} attempt=${attempt + 1} startSec=${formatSeconds(currentTime)} durationSec=${formatSeconds(segmentDuration)}"
                    )
                    val command = "-ss ${formatSeconds(currentTime)} " +
                        "-i \"${audioFile.absolutePath}\" " +
                        "-t ${formatSeconds(segmentDuration)} " +
                        "-c copy " +
                        "-avoid_negative_ts make_zero " +
                        "\"${outputFile.absolutePath}\""

                    val session = FFmpegKit.execute(command)

                    if (ReturnCode.isSuccess(session.returnCode)) {
                        if (outputFile.exists() && outputFile.length() > 0) {
                            if (outputFile.length() <= MAX_CHUNK_SIZE_BYTES) {
                                Log.d(
                                    tag,
                                    "splitAudioIfNeeded chunk=${chunkIndex + 1} success path=${outputFile.name} sizeBytes=${outputFile.length()}"
                                )
                                splitFiles.add(outputFile)
                                splitSuccess = true
                            } else {
                                Log.w(
                                    tag,
                                    "splitAudioIfNeeded chunk=${chunkIndex + 1} oversize sizeBytes=${outputFile.length()} retrying with shorter duration"
                                )
                                outputFile.delete()
                                segmentDuration *= 0.85
                                attempt++
                            }
                        } else {
                            return@withContext Result.failure(Exception("Split file was not created"))
                        }
                    } else {
                        val errorOutput = session.failStackTrace ?: "Unknown error"
                        Log.e(tag, "splitAudioIfNeeded ffmpeg failed chunk=${chunkIndex + 1} error=$errorOutput")
                        return@withContext Result.failure(Exception("Failed to split audio: $errorOutput"))
                    }
                }

                if (!splitSuccess) {
                    Log.e(tag, "splitAudioIfNeeded failed after retries chunk=${chunkIndex + 1}")
                    return@withContext Result.failure(Exception("Failed to split audio into WhatsApp-sized parts"))
                }

                currentTime += segmentDuration
                chunkIndex++
            }

            if (splitFiles.isNotEmpty()) {
                Log.d(tag, "splitAudioIfNeeded deleting source after split path=${audioFile.name}")
                audioFile.delete()
            }

            val totalOutputSize = splitFiles.sumOf { it.length() }
            Log.d(
                tag,
                "splitAudioIfNeeded completed parts=${splitFiles.size} totalOutputBytes=$totalOutputSize sourceBytes=$sourceSize"
            )

            Result.success(splitFiles)
        } catch (e: Exception) {
            Log.e(tag, "splitAudioIfNeeded exception", e)
            Result.failure(e)
        }
    }

    private fun getMediaDuration(mediaFile: File): Double? {
        return try {
            val command = "-i \"${mediaFile.absolutePath}\""
            val session = FFmpegKit.execute(command)
            val output = session.output ?: session.allLogsAsString

            val durationRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")
            val match = durationRegex.find(output)

            if (match != null) {
                val hours = match.groupValues[1].toInt()
                val minutes = match.groupValues[2].toInt()
                val seconds = match.groupValues[3].toInt()
                val centiseconds = match.groupValues[4].toInt()
                hours * 3600.0 + minutes * 60.0 + seconds + centiseconds / 100.0
            } else {
                val estimatedBitrate = 128_000.0
                val fileSizeBits = mediaFile.length() * 8.0
                fileSizeBits / estimatedBitrate
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatSeconds(value: Double): String {
        return String.format(java.util.Locale.US, "%.3f", value)
    }

    private fun stripSuffix(baseName: String): String {
        return if (baseName.endsWith(FILE_NAME_SUFFIX)) {
            baseName.dropLast(FILE_NAME_SUFFIX.length).trimEnd()
        } else {
            baseName
        }
    }

    private fun appendSuffix(baseName: String): String {
        val trimmed = baseName.trim()
        return if (trimmed.endsWith(FILE_NAME_SUFFIX)) trimmed else trimmed + FILE_NAME_SUFFIX
    }
}
