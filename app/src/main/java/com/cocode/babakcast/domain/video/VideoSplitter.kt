package com.cocode.babakcast.domain.video

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cocode.babakcast.data.model.VideoChapter
import com.cocode.babakcast.data.model.VideoInfo
import com.cocode.babakcast.domain.FfmpegCommands
import com.cocode.babakcast.domain.split.ChapterSplitEstimator
import com.cocode.babakcast.domain.split.SplitDecision
import com.cocode.babakcast.domain.split.SplitMode
import com.cocode.babakcast.domain.split.SplitSize
import com.cocode.babakcast.util.DownloadFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoSplitter @Inject constructor() {

    companion object {
        internal const val MAX_CHUNK_SIZE_BYTES = SplitSize.DEFAULT_BYTES
        private const val MAX_SPLIT_ATTEMPTS = 5
        // Aim slightly below the cap so the first attempt usually fits on one shot.
        private fun targetChunkSize(maxChunk: Long): Long = (maxChunk * 15) / 16
    }

    suspend fun splitVideoIfNeeded(
        videoInfo: VideoInfo,
        splitMode: SplitMode = SplitMode.BY_SIZE,
        chunkSizeBytes: Long = MAX_CHUNK_SIZE_BYTES,
        chapterHints: List<VideoChapter> = videoInfo.chapters,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)? = null
    ): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoFile = videoInfo.file ?: return@withContext Result.success(videoInfo)
            val sourceSize = videoInfo.fileSizeBytes.takeIf { it > 0L } ?: videoFile.length()
            if (SplitDecision.skipFor(splitMode, sourceSize, chunkSizeBytes)) {
                return@withContext Result.success(videoInfo)
            }

            val duration = getVideoDuration(videoFile)
                ?: return@withContext Result.failure(Exception("Could not determine video duration"))
            if (duration <= 0.0) {
                return@withContext Result.failure(Exception("Invalid video duration"))
            }

            val bytesPerSecond = sourceSize / duration
            if (bytesPerSecond <= 0.0) {
                return@withContext Result.failure(Exception("Invalid bitrate estimate"))
            }

            val outputDir = videoFile.parentFile
                ?: return@withContext Result.failure(Exception("Invalid output directory"))
            val baseName = videoFile.nameWithoutExtension

            when (splitMode) {
                SplitMode.NONE -> error("NONE should have been skipped by SplitDecision")
                SplitMode.CHAPTERS -> splitByChapters(
                    videoInfo = videoInfo,
                    videoFile = videoFile,
                    outputDir = outputDir,
                    baseName = baseName,
                    sourceSize = sourceSize,
                    duration = duration,
                    chapterHints = chapterHints,
                    onProgress = onProgress
                )
                SplitMode.BY_SIZE -> splitBySize(
                    videoInfo = videoInfo,
                    videoFile = videoFile,
                    outputDir = outputDir,
                    baseName = baseName,
                    duration = duration,
                    bytesPerSecond = bytesPerSecond,
                    chunkSizeBytes = chunkSizeBytes,
                    onProgress = onProgress
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun splitBySize(
        videoInfo: VideoInfo,
        videoFile: File,
        outputDir: File,
        baseName: String,
        duration: Double,
        bytesPerSecond: Double,
        chunkSizeBytes: Long,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)?
    ): Result<VideoInfo> {
        val chunkDuration = targetChunkSize(chunkSizeBytes).toDouble() / bytesPerSecond
        val splitFiles = mutableListOf<File>()
        val estimatedParts = kotlin.math.ceil(duration / chunkDuration).toInt().coerceAtLeast(1)

        var currentTime = 0.0
        var chunkIndex = 0
        while (currentTime < duration) {
            onProgress?.invoke(chunkIndex + 1, estimatedParts)
            val partNumber = DownloadFileParser.formatPartNumber(chunkIndex + 1, estimatedParts)
            val outputFile = File(outputDir, "${baseName}_part${partNumber}.mp4")

            var segmentDuration = minOf(chunkDuration, duration - currentTime)
            var attempt = 0
            var splitSuccess = false

            while (attempt < MAX_SPLIT_ATTEMPTS && !splitSuccess) {
                val command = FfmpegCommands.buildCopySegmentCommand(
                    inputFile = videoFile,
                    outputFile = outputFile,
                    startSeconds = currentTime,
                    durationSeconds = segmentDuration
                )
                val session = FFmpegKit.execute(command)

                if (!ReturnCode.isSuccess(session.returnCode)) {
                    val errorOutput = session.failStackTrace ?: "Unknown error"
                    return Result.failure(Exception("Failed to split video: $errorOutput"))
                }
                val producedBytes = outputFile.length()
                if (producedBytes <= 0) {
                    return Result.failure(Exception("Split file was not created"))
                }
                if (producedBytes <= chunkSizeBytes) {
                    splitFiles.add(outputFile)
                    splitSuccess = true
                } else {
                    outputFile.delete()
                    segmentDuration *= 0.85
                    attempt++
                }
            }

            if (!splitSuccess) {
                cleanupFiles(splitFiles)
                return Result.failure(Exception("Failed to split video into requested chunk size"))
            }

            currentTime += segmentDuration
            chunkIndex++
        }

        if (splitFiles.isNotEmpty()) {
            videoFile.delete()
        }
        return Result.success(videoInfo.copy(file = null, splitFiles = splitFiles))
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
            val command = FfmpegCommands.buildCopySegmentCommand(
                inputFile = videoFile,
                outputFile = outputFile,
                startSeconds = estimated.chapter.startTimeSeconds,
                durationSeconds = segmentDuration
            )

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
            val command = FfmpegCommands.buildProbeCommand(videoFile)
            
            val session = FFmpegKit.execute(command)
            val output = session.output ?: session.allLogsAsString
            
            FfmpegCommands.parseDurationSeconds(output)?.let { return it }
            
            // Fallback: estimate from file size (rough approximation)
            // Average bitrate assumption: ~2 Mbps for 720p
            val estimatedBitrate = 2_000_000.0 // bits per second
            val fileSizeBits = videoFile.length() * 8.0
            return fileSizeBits / estimatedBitrate
        } catch (e: Exception) {
            return null
        }
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
