package com.cocode.babakcast.domain.video

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Cuts one segment out of a downloaded video, starting on the first frame at or
 * after the requested time.
 *
 * FFmpeg is deliberately not used here, unlike [VideoSplitter]. A `-c copy` cut
 * can only begin on a keyframe, which drags the start of the segment seconds back
 * from the time the user asked for, and the bundled ffmpeg-kit-audio build ships
 * no H.264 encoder to re-encode with. Media3's Transformer re-encodes through
 * MediaCodec, so the cut is frame-accurate.
 *
 * Media3's trim optimization is not used: it only stitches the re-encoded head to
 * the copied tail when the device encoder's SPS/PPS match the source's, and on a
 * real device against YouTube encodes it failed with FORMAT_MISMATCH, then redid
 * the whole export. The segment is therefore always re-encoded, at the source's
 * bitrate in constant-bitrate mode so the clip stays near its share of the original
 * size. Left to itself the encoder picked 5-15x the source bitrate.
 */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class VideoTrimmer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * @return the segment as a new file. The source is deleted on success, so
     *   only the requested segment survives.
     */
    suspend fun trim(sourceFile: File, range: TrimRange): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            Log.e(TAG, "trim aborted: source missing path=${sourceFile.absolutePath}")
            return@withContext Result.failure(Exception("Source video not found"))
        }
        val outputDir = sourceFile.parentFile
            ?: return@withContext Result.failure(Exception("Invalid output directory"))

        val outputFile = File(outputDir, ClipFileName.forSource(sourceFile))
        if (outputFile.absolutePath == sourceFile.absolutePath) {
            return@withContext Result.failure(Exception("Trim output would overwrite its source"))
        }
        // Export beside the target, not over it: a failed re-cut of the same
        // range must leave the previous clip intact. The external cache shares a
        // volume with the videos directory, so the final rename is atomic.
        val tempFile = File(context.externalCacheDir ?: outputDir, "trim-${System.nanoTime()}.mp4")

        Log.d(
            TAG,
            "trim start source=${sourceFile.name} startMs=${range.startMs} " +
                "endMs=${range.endMs} durationMs=${range.durationMs}"
        )

        // Transformer delivers its callbacks on the Looper of the thread that
        // built it, so only the export itself hops to the main thread — every
        // file check around it stays on IO.
        val bitrate = sourceBitrate(sourceFile)
        withContext(Dispatchers.Main) { export(sourceFile, tempFile, range, bitrate) }
            .mapCatching { clip ->
                if (!clip.exists() || clip.length() <= 0L) {
                    throw Exception("Trimmed file was not created")
                }
                if (!clip.renameTo(outputFile)) throw Exception("Could not save the trimmed file")
                sourceFile.delete()
                Log.d(TAG, "trim success output=${outputFile.name} outputBytes=${outputFile.length()}")
                outputFile
            }
            .onFailure { error -> Log.e(TAG, "trim failed", error) }
            .also { if (tempFile.exists()) tempFile.delete() }
    }

    private suspend fun export(
        sourceFile: File,
        outputFile: File,
        range: TrimRange,
        bitrate: Int?
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(sourceFile))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build()
            )
            .build()

        val transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory(bitrate))
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    Log.d(
                        TAG,
                        "export done videoEncoder=${exportResult.videoEncoderName} " +
                            "requestedBitrate=$bitrate averageVideoBitrate=${exportResult.averageVideoBitrate}"
                    )
                    if (continuation.isActive) continuation.resume(Result.success(outputFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) continuation.resume(Result.failure(exportException))
                }
            })
            .build()

        // cancel() is main-thread-only, and the cancellation handler runs on
        // whichever thread cancelled the coroutine.
        continuation.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post { transformer.cancel() }
        }

        transformer.start(EditedMediaItem.Builder(mediaItem).build(), outputFile.absolutePath)
    }

    private fun encoderFactory(bitrate: Int?): DefaultEncoderFactory {
        val builder = DefaultEncoderFactory.Builder(context)
        if (bitrate != null) {
            builder.setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(bitrate)
                    .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                    .build()
            )
        }
        return builder.build()
    }

    /** The source's overall bitrate in bits per second, or null when unknown. */
    private fun sourceBitrate(file: File): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        } catch (e: RuntimeException) {
            Log.w(TAG, "source bitrate unknown for ${file.name}", e)
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        private const val TAG = "VideoTrimmer"
    }
}
