package com.cocode.babakcast.domain.video

import com.cocode.babakcast.data.model.VideoInfo
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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
        private const val MAX_CHUNK_SIZE_BYTES = 16 * 1024 * 1024 // 16 MB limit
        private const val TARGET_CHUNK_SIZE_BYTES = 15 * 1024 * 1024 // 15 MB target to reduce tiny chunks
        private const val MAX_SPLIT_ATTEMPTS = 5
        private const val FILE_NAME_SUFFIX = " - Visit BabakCast"
    }

    /**
     * Split video into chunks if it exceeds 16MB
     */
    suspend fun splitVideoIfNeeded(
        videoInfo: VideoInfo,
        onProgress: ((currentPart: Int, totalParts: Int) -> Unit)? = null
    ): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoFile = videoInfo.file ?: return@withContext Result.success(videoInfo)

            if (!videoInfo.needsSplitting) {
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

            val bytesPerSecond = videoInfo.fileSizeBytes / duration
            if (bytesPerSecond <= 0.0) {
                return@withContext Result.failure(Exception("Invalid bitrate estimate"))
            }

            val chunkDuration = TARGET_CHUNK_SIZE_BYTES.toDouble() / bytesPerSecond

            // Split video
            val outputDir = videoFile.parentFile
            val baseName = stripSuffix(videoFile.nameWithoutExtension)
            val splitFiles = mutableListOf<File>()

            val estimatedParts = kotlin.math.ceil(duration / chunkDuration).toInt().coerceAtLeast(1)
            val indexWidth = estimatedParts.toString().length

            var currentTime = 0.0
            var chunkIndex = 0

            while (currentTime < duration) {
                onProgress?.invoke(chunkIndex + 1, estimatedParts)
                val partNumber = (chunkIndex + 1).toString().padStart(indexWidth, '0')
                val outputBaseName = "${baseName}_part${partNumber}"
                val outputFile = File(outputDir, "${appendSuffix(outputBaseName)}.mp4")
                
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
