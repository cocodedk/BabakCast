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
        private const val MAX_CHUNK_SIZE_BYTES = 16 * 1024 * 1024 // 16 MB
        private const val TARGET_CHUNK_SIZE_BYTES = 15 * 1024 * 1024 // 15 MB (safety margin)
    }

    /**
     * Split video into chunks if it exceeds 16MB
     */
    suspend fun splitVideoIfNeeded(videoInfo: VideoInfo): Result<VideoInfo> = withContext(Dispatchers.IO) {
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
            val bytesPerSecond = videoInfo.fileSizeBytes / duration
            val chunkDuration = TARGET_CHUNK_SIZE_BYTES.toDouble() / bytesPerSecond

            // Split video
            val outputDir = videoFile.parentFile
            val baseName = videoFile.nameWithoutExtension
            val splitFiles = mutableListOf<File>()

            var currentTime = 0.0
            var chunkIndex = 0

            while (currentTime < duration) {
                val outputFile = File(outputDir, "${baseName}_part${chunkIndex + 1}.mp4")
                
                // Calculate segment duration
                val segmentDuration = minOf(chunkDuration, duration - currentTime)

                // FFmpeg command: extract segment using copy codec for speed
                val command = "-ss ${currentTime.toInt()} " +
                    "-i \"${videoFile.absolutePath}\" " +
                    "-t ${segmentDuration.toInt()} " +
                    "-c copy " +
                    "-avoid_negative_ts make_zero " +
                    "\"${outputFile.absolutePath}\""

                val session = FFmpegKit.execute(command)
                
                if (ReturnCode.isSuccess(session.returnCode)) {
                    if (outputFile.exists() && outputFile.length() > 0) {
                        splitFiles.add(outputFile)
                    } else {
                        return@withContext Result.failure(Exception("Split file was not created"))
                    }
                } else {
                    val errorOutput = session.failStackTrace ?: "Unknown error"
                    return@withContext Result.failure(
                        Exception("Failed to split video: $errorOutput")
                    )
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
}
