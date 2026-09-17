package com.cocode.babakcast.domain.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Cuts one segment out of a downloaded video, exact to the requested millisecond.
 *
 * FFmpeg is deliberately not used here, unlike [VideoSplitter]. A `-c copy` cut
 * can only begin on a keyframe, which drags the start of the segment seconds back
 * from the time the user asked for, and the bundled ffmpeg-kit-audio build ships
 * no H.264 encoder to re-encode with. Media3's Transformer re-encodes through
 * MediaCodec, so the cut is frame-accurate; trim optimization keeps that cheap by
 * transmuxing everything after the first keyframe rather than the whole segment,
 * and falls back to a full export on its own when a source cannot be optimized.
 */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class, ExperimentalApi::class)
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
        if (outputFile.exists()) outputFile.delete()

        Log.d(
            TAG,
            "trim start source=${sourceFile.name} startMs=${range.startMs} " +
                "endMs=${range.endMs} durationMs=${range.durationMs}"
        )

        // Transformer delivers its callbacks on the Looper of the thread that
        // built it, so only the export itself hops to the main thread — every
        // file check around it stays on IO.
        withContext(Dispatchers.Main) { export(sourceFile, outputFile, range) }
            .mapCatching { clip ->
                if (!clip.exists() || clip.length() <= 0L) {
                    throw Exception("Trimmed file was not created")
                }
                sourceFile.delete()
                Log.d(TAG, "trim success output=${clip.name} outputBytes=${clip.length()}")
                clip
            }
            .onFailure { error ->
                Log.e(TAG, "trim failed", error)
                if (outputFile.exists()) outputFile.delete()
            }
    }

    private suspend fun export(
        sourceFile: File,
        outputFile: File,
        range: TrimRange
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
            .experimentalSetTrimOptimizationEnabled(true)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
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

    private companion object {
        private const val TAG = "VideoTrimmer"
    }
}
