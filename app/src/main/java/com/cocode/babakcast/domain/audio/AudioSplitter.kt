package com.cocode.babakcast.domain.audio

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cocode.babakcast.data.model.VideoChapter
import com.cocode.babakcast.domain.split.ChapterSplitEstimator
import com.cocode.babakcast.domain.split.SplitMode
import com.cocode.babakcast.util.DownloadFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmName

@Singleton
class AudioSplitter @Inject constructor() {

    companion object {
        private const val TAG = "AudioSplitter"
        internal const val MAX_CHUNK_SIZE_BYTES = 16L * 1024 * 1024
        private const val TARGET_CHUNK_SIZE_BYTES = 15L * 1024 * 1024
        private const val MAX_SPLIT_ATTEMPTS = 5
        private const val FILE_NAME_SUFFIX = " - Visit BabakCast"
        private const val DEFAULT_EXTENSION = "mp3"
    }

    @JvmName("splitAudioIfNeeded")
    suspend fun splitAudioIfNeeded(
        audioFile: File,
        chapterHints: List<VideoChapter> = emptyList(),
        splitMode: SplitMode = SplitMode.SIZE_16MB,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)? = null
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists()) {
                Log.e(TAG, "splitAudioIfNeeded aborted: source file missing path=${audioFile.absolutePath}")
                return@withContext Result.failure(Exception("Audio file not found"))
            }

            val sourceSize = audioFile.length()
            Log.d(
                TAG,
                "splitAudioIfNeeded start name=${audioFile.name} sizeBytes=$sourceSize maxChunkBytes=$MAX_CHUNK_SIZE_BYTES splitMode=$splitMode chapterHints=${chapterHints.size}"
            )

            if (sourceSize <= MAX_CHUNK_SIZE_BYTES && splitMode == SplitMode.SIZE_16MB) {
                Log.d(TAG, "splitAudioIfNeeded skip: size within limit, returning original file")
                return@withContext Result.success(listOf(audioFile))
            }

            val duration = getMediaDuration(audioFile)
                ?: return@withContext Result.failure(Exception("Could not determine audio duration"))

            if (duration <= 0.0) {
                return@withContext Result.failure(Exception("Invalid audio duration"))
            }

            val bytesPerSecond = sourceSize / duration
            if (bytesPerSecond <= 0.0) {
                return@withContext Result.failure(Exception("Invalid bitrate estimate"))
            }

            val outputDir = audioFile.parentFile
                ?: return@withContext Result.failure(Exception("Invalid output directory"))
            val baseName = stripSuffix(audioFile.nameWithoutExtension)
            val outputExtension = audioFile.extension.ifBlank { DEFAULT_EXTENSION }

            if (splitMode == SplitMode.CHAPTERS) {
                return@withContext splitByChapters(
                    audioFile = audioFile,
                    outputDir = outputDir,
                    baseName = baseName,
                    outputExtension = outputExtension,
                    sourceSize = sourceSize,
                    duration = duration,
                    chapterHints = chapterHints,
                    onProgress = onProgress
                )
            }

            val chunkDuration = TARGET_CHUNK_SIZE_BYTES.toDouble() / bytesPerSecond
            val splitFiles = mutableListOf<File>()

            val estimatedParts = kotlin.math.ceil(duration / chunkDuration).toInt().coerceAtLeast(1)

            Log.d(
                TAG,
                "splitAudioIfNeeded planning durationSec=${formatSeconds(duration)} bytesPerSec=${"%.2f".format(java.util.Locale.US, bytesPerSecond)} targetChunkSec=${formatSeconds(chunkDuration)} estimatedParts=$estimatedParts"
            )

            var currentTime = 0.0
            var chunkIndex = 0

            while (currentTime < duration) {
                val currentPart = (chunkIndex + 1).coerceAtMost(estimatedParts)
                onProgress?.invoke(currentPart, estimatedParts)
                val partNumber = DownloadFileParser.formatPartNumber(chunkIndex + 1, estimatedParts)
                val outputBaseName = "${baseName}_part${partNumber}"
                val outputFile = File(outputDir, "${appendSuffix(outputBaseName)}.$outputExtension")

                var segmentDuration = minOf(chunkDuration, duration - currentTime)
                var attempt = 0
                var splitSuccess = false

                while (attempt < MAX_SPLIT_ATTEMPTS && !splitSuccess) {
                    Log.d(
                        TAG,
                        "splitAudioIfNeeded chunk=${chunkIndex + 1} attempt=${attempt + 1} startSec=${formatSeconds(currentTime)} durationSec=${formatSeconds(segmentDuration)}"
                    )
                    val command = "-ss ${formatSeconds(currentTime)} " +
                        "-i \"${audioFile.absolutePath}\" " +
                        "-t ${formatSeconds(segmentDuration)} " +
                        "-c copy " +
                        "-avoid_negative_ts make_zero " +
                        "-y " +
                        "\"${outputFile.absolutePath}\""

                    val session = FFmpegKit.execute(command)

                    if (ReturnCode.isSuccess(session.returnCode)) {
                        if (outputFile.exists() && outputFile.length() > 0) {
                            if (outputFile.length() <= MAX_CHUNK_SIZE_BYTES) {
                                Log.d(
                                    TAG,
                                    "splitAudioIfNeeded chunk=${chunkIndex + 1} success path=${outputFile.name} sizeBytes=${outputFile.length()}"
                                )
                                splitFiles.add(outputFile)
                                splitSuccess = true
                            } else {
                                Log.w(
                                    TAG,
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
                        Log.e(TAG, "splitAudioIfNeeded ffmpeg failed chunk=${chunkIndex + 1} error=$errorOutput")
                        return@withContext Result.failure(Exception("Failed to split audio: $errorOutput"))
                    }
                }

                if (!splitSuccess) {
                    Log.e(TAG, "splitAudioIfNeeded failed after retries chunk=${chunkIndex + 1}")
                    cleanupFiles(splitFiles)
                    return@withContext Result.failure(Exception("Failed to split audio into WhatsApp-sized parts"))
                }

                currentTime += segmentDuration
                chunkIndex++
            }

            if (splitFiles.isNotEmpty()) {
                Log.d(TAG, "splitAudioIfNeeded deleting source after split path=${audioFile.name}")
                audioFile.delete()
            }

            val totalOutputSize = splitFiles.sumOf { it.length() }
            Log.d(
                TAG,
                "splitAudioIfNeeded completed parts=${splitFiles.size} totalOutputBytes=$totalOutputSize sourceBytes=$sourceSize"
            )

            Result.success(splitFiles)
        } catch (e: Exception) {
            Log.e(TAG, "splitAudioIfNeeded exception", e)
            Result.failure(e)
        }
    }

    private fun splitByChapters(
        audioFile: File,
        outputDir: File,
        baseName: String,
        outputExtension: String,
        sourceSize: Long,
        duration: Double,
        chapterHints: List<VideoChapter>,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)?
    ): Result<List<File>> {
        val estimatedChapters = ChapterSplitEstimator.estimateChapterBytes(
            chapters = chapterHints,
            totalDurationSeconds = duration,
            totalBytes = sourceSize
        )
        if (estimatedChapters.isEmpty()) {
            return Result.failure(Exception("No valid chapters available for chapter split"))
        }

        val oversized = ChapterSplitEstimator.firstOversizedChapter(
            estimatedChapters = estimatedChapters,
            maxChunkBytes = MAX_CHUNK_SIZE_BYTES
        )
        if (oversized != null) {
            val label = oversized.chapter.title.ifBlank { "Unnamed chapter" }
            val sizeMb = oversized.estimatedBytes.toDouble() / (1024.0 * 1024.0)
            return Result.failure(
                Exception(
                    "Chapter split exceeds 16MB for \"$label\" (estimated ${
                        String.format(java.util.Locale.US, "%.1f", sizeMb)
                    } MB). Choose 16 MB split."
                )
            )
        }

        val splitFiles = mutableListOf<File>()
        val totalParts = estimatedChapters.size

        for ((index, estimated) in estimatedChapters.withIndex()) {
            val currentPart = index + 1
            onProgress?.invoke(currentPart, totalParts)
            val outputFile = buildOutputFile(
                outputDir = outputDir,
                baseName = baseName,
                outputExtension = outputExtension,
                partIndex = currentPart,
                totalParts = totalParts
            )
            val segmentDuration = estimated.chapter.endTimeSeconds - estimated.chapter.startTimeSeconds
            val command = "-ss ${formatSeconds(estimated.chapter.startTimeSeconds)} " +
                "-i \"${audioFile.absolutePath}\" " +
                "-t ${formatSeconds(segmentDuration)} " +
                "-c copy " +
                "-avoid_negative_ts make_zero " +
                "-y " +
                "\"${outputFile.absolutePath}\""

            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                cleanupFiles(splitFiles)
                val errorOutput = session.failStackTrace ?: "Unknown error"
                return Result.failure(Exception("Failed to split audio by chapter: $errorOutput"))
            }
            if (!outputFile.exists() || outputFile.length() <= 0L) {
                cleanupFiles(splitFiles)
                return Result.failure(Exception("Chapter split file was not created"))
            }
            if (outputFile.length() > MAX_CHUNK_SIZE_BYTES) {
                cleanupFiles(splitFiles + outputFile)
                val label = estimated.chapter.title.ifBlank { "Unnamed chapter" }
                val sizeMb = outputFile.length().toDouble() / (1024.0 * 1024.0)
                return Result.failure(
                    Exception(
                        "Chapter split produced chunk larger than 16MB for \"$label\" (${
                            String.format(java.util.Locale.US, "%.1f", sizeMb)
                        } MB). Choose 16 MB split."
                    )
                )
            }

            splitFiles.add(outputFile)
        }

        if (splitFiles.isNotEmpty()) {
            Log.d(TAG, "splitAudioIfNeeded deleting source after chapter split path=${audioFile.name}")
            audioFile.delete()
        }

        return Result.success(splitFiles)
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

    private fun buildOutputFile(
        outputDir: File,
        baseName: String,
        outputExtension: String,
        partIndex: Int,
        totalParts: Int
    ): File {
        val partNumber = DownloadFileParser.formatPartNumber(partIndex, totalParts)
        val outputBaseName = "${baseName}_part${partNumber}"
        return File(outputDir, "${appendSuffix(outputBaseName)}.$outputExtension")
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

    private fun cleanupFiles(files: List<File>) {
        files.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
