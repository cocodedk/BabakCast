package com.cocode.babakcast.domain.video

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cocode.babakcast.data.model.VideoChapter
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.domain.split.ChapterSplitEstimator
import com.cocode.babakcast.domain.split.SplitMode
import com.cocode.babakcast.util.DownloadFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Splits videos into chunks of ≤16MB each
 */
@Singleton
class VideoSplitter @Inject constructor() {

    companion object {
        internal const val MAX_CHUNK_SIZE_BYTES = 16L * 1024 * 1024 // 16 MB limit
        private const val TARGET_CHUNK_SIZE_BYTES = 15L * 1024 * 1024 // 15 MB target to reduce tiny chunks
        private const val MAX_SPLIT_ATTEMPTS = 5
    }

    /**
     * Split video into chunks if it exceeds 16MB
     */
    suspend fun splitVideoIfNeeded(
        videoInfo: VideoInfo,
        splitMode: SplitMode = SplitMode.SIZE_16MB,
        chapterHints: List<VideoChapter> = videoInfo.chapters,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)? = null
    ): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoFile = videoInfo.file ?: return@withContext Result.success(videoInfo)

            if (!videoInfo.needsSplitting && splitMode == SplitMode.SIZE_16MB) {
                return@withContext Result.success(videoInfo)
            }

            // Get video duration
            val duration = getVideoDuration(videoFile)
                ?: return@withContext Result.failure(Exception("Could not determine video duration"))

            // Calculate target chunk duration (in seconds)
            // Estimate: file_size / duration = bytes_per_second
            // chunk_duration = target_chunk_size / bytes_per_second
            if (duration <= 0.0) {
                return@withContext Result.failure(Exception("Invalid video duration"))
            }

            val sourceSize = videoInfo.fileSizeBytes.takeIf { it > 0L } ?: videoFile.length()
            val bytesPerSecond = sourceSize / duration
            if (bytesPerSecond <= 0.0) {
                return@withContext Result.failure(Exception("Invalid bitrate estimate"))
            }

            val outputDir = videoFile.parentFile
                ?: return@withContext Result.failure(Exception("Invalid output directory"))
            val baseName = videoFile.nameWithoutExtension

            if (splitMode == SplitMode.CHAPTERS) {
                return@withContext splitByChapters(
                    videoInfo = videoInfo,
                    videoFile = videoFile,
                    outputDir = outputDir,
                    baseName = baseName,
                    sourceSize = sourceSize,
                    duration = duration,
                    chapterHints = chapterHints,
                    onProgress = onProgress
                )
            }

            val chunkDuration = TARGET_CHUNK_SIZE_BYTES.toDouble() / bytesPerSecond
            val splitFiles = mutableListOf<File>()

            val estimatedParts = kotlin.math.ceil(duration / chunkDuration).toInt().coerceAtLeast(1)

            var currentTime = 0.0
            var chunkIndex = 0

            while (currentTime < duration) {
                onProgress?.invoke(chunkIndex + 1, estimatedParts)
                val partNumber = DownloadFileParser.formatPartNumber(chunkIndex + 1, estimatedParts)
                val outputBaseName = "${baseName}_part${partNumber}"
                val outputFile = File(outputDir, "${outputBaseName}.mp4")
                
                // Calculate segment duration
                var segmentDuration = minOf(chunkDuration, duration - currentTime)
                var attempt = 0
                var splitSuccess = false

                while (attempt < MAX_SPLIT_ATTEMPTS && !splitSuccess) {
                    // FFmpeg command: extract segment using copy codec for speed
                    val command = "-ss ${formatSeconds(currentTime)} " +
                        "-i \"${videoFile.absolutePath}\" " +
                        "-t ${formatSeconds(segmentDuration)} " +
                        "-c copy " +
                        "-avoid_negative_ts make_zero " +
                        "-y " +
                        "\"${outputFile.absolutePath}\""

                    val session = FFmpegKit.execute(command)
                    
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        if (outputFile.exists() && outputFile.length() > 0) {
                            if (outputFile.length() <= MAX_CHUNK_SIZE_BYTES) {
                                splitFiles.add(outputFile)
                                splitSuccess = true
                            } else {
                                outputFile.delete()
                                segmentDuration *= 0.85
                                attempt++
                            }
                        } else {
                            return@withContext Result.failure(Exception("Split file was not created"))
                        }
                    } else {
                        val errorOutput = session.failStackTrace ?: "Unknown error"
                        return@withContext Result.failure(
                            Exception("Failed to split video: $errorOutput")
                        )
                    }
                }

                if (!splitSuccess) {
                    cleanupFiles(splitFiles)
                    return@withContext Result.failure(Exception("Failed to split video into WhatsApp-sized parts"))
                }

                currentTime += segmentDuration
                chunkIndex++
            }

            // Delete original file after successful split
            if (splitFiles.isNotEmpty()) {
                videoFile.delete()
            }

            Result.success(
                videoInfo.copy(
                    file = null,
                    splitFiles = splitFiles
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun splitByChapters(
        videoInfo: VideoInfo,
        videoFile: File,
        outputDir: File,
        baseName: String,
        sourceSize: Long,
        duration: Double,
        chapterHints: List<VideoChapter>,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)?
    ): Result<VideoInfo> {
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
            onProgress?.invoke(index + 1, totalParts)
            val outputFile = buildOutputFile(
                outputDir = outputDir,
                baseName = baseName,
                partIndex = index + 1,
                totalParts = totalParts,
                extension = "mp4"
            )
            val segmentDuration = estimated.chapter.endTimeSeconds - estimated.chapter.startTimeSeconds
            val command = "-ss ${formatSeconds(estimated.chapter.startTimeSeconds)} " +
                "-i \"${videoFile.absolutePath}\" " +
                "-t ${formatSeconds(segmentDuration)} " +
                "-c copy " +
                "-avoid_negative_ts make_zero " +
                "-y " +
                "\"${outputFile.absolutePath}\""

            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                cleanupFiles(splitFiles)
                val errorOutput = session.failStackTrace ?: "Unknown error"
                return Result.failure(Exception("Failed to split video by chapter: $errorOutput"))
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
            videoFile.delete()
        }

        return Result.success(
            videoInfo.copy(
                file = null,
                splitFiles = splitFiles
            )
        )
    }

    /**
     * Get video duration in seconds using FFmpeg
     */
    private fun getVideoDuration(videoFile: File): Double? {
        try {
            // Use FFmpeg to get duration
            val command = "-i \"${videoFile.absolutePath}\""
            
            val session = FFmpegKit.execute(command)
            val output = session.output ?: session.allLogsAsString
            
            // Parse duration from output: Duration: HH:MM:SS.mmm
            val durationRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")
            val match = durationRegex.find(output)
            
            if (match != null) {
                val hours = match.groupValues[1].toInt()
                val minutes = match.groupValues[2].toInt()
                val seconds = match.groupValues[3].toInt()
                val centiseconds = match.groupValues[4].toInt()
                
                return hours * 3600.0 + minutes * 60.0 + seconds + centiseconds / 100.0
            }
            
            // Fallback: estimate from file size (rough approximation)
            // Average bitrate assumption: ~2 Mbps for 720p
            val estimatedBitrate = 2_000_000.0 // bits per second
            val fileSizeBits = videoFile.length() * 8.0
            return fileSizeBits / estimatedBitrate
        } catch (e: Exception) {
            return null
        }
    }

    private fun formatSeconds(value: Double): String {
        return String.format(java.util.Locale.US, "%.3f", value)
    }

    private fun buildOutputFile(
        outputDir: File,
        baseName: String,
        partIndex: Int,
        totalParts: Int,
        extension: String
    ): File {
        val partNumber = DownloadFileParser.formatPartNumber(partIndex, totalParts)
        val outputBaseName = "${baseName}_part${partNumber}"
        return File(outputDir, "${outputBaseName}.$extension")
    }


    private fun cleanupFiles(files: List<File>) {
        files.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
