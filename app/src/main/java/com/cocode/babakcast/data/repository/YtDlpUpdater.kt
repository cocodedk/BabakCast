package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

/** The yt-dlp release channel both flavours read. YouTube changes often, and fixes land here first. */
internal val YTDLP_CHANNEL: YoutubeDL.UpdateChannel = YoutubeDL.UpdateChannel.NIGHTLY

/** How a yt-dlp update the user started ended. */
sealed interface YtDlpUpdateResult {
    /** A newer yt-dlp is installed. */
    data object Updated : YtDlpUpdateResult
    data object AlreadyLatest : YtDlpUpdateResult

    /** A download is running, and the binary must not be swapped under it. */
    data object DownloadRunning : YtDlpUpdateResult
    data object Failed : YtDlpUpdateResult
}

/**
 * Keeps a yt-dlp update from overlapping a download. YoutubeDL.execute() is not synchronized
 * against the updater, which deletes and rewrites the yt-dlp binary, so a download that
 * started during the swap could find no binary at all.
 *
 * Downloads share the read side. An update takes the write side, and only when no download
 * is running: it reports [DownloadRunning] instead of making the user wait on a long download.
 */
internal class YtDlpGate {
    private val lock = ReentrantReadWriteLock()

    fun <T> download(block: () -> T): T = lock.read(block)

    fun update(perform: () -> YtDlpUpdateResult): YtDlpUpdateResult {
        if (!lock.writeLock().tryLock()) return YtDlpUpdateResult.DownloadRunning
        try {
            return perform()
        } finally {
            lock.writeLock().unlock()
        }
    }
}

/** Maps the library's answer to ours; a null answer means the library did not say. */
internal fun toUpdateResult(status: YoutubeDL.UpdateStatus?): YtDlpUpdateResult = when (status) {
    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateResult.Updated
    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YtDlpUpdateResult.AlreadyLatest
    null -> YtDlpUpdateResult.Failed
}

/**
 * The yt-dlp update a user starts from Settings (the fdroid flavour never updates on its own).
 * It asks GitHub for the latest yt-dlp release and replaces the bundled copy.
 */
object YtDlpUpdater {
    private const val TAG = "YtDlpUpdater"

    private val gate = YtDlpGate()

    /** Every yt-dlp run goes through here, so an update cannot overlap one. */
    internal fun execute(
        request: YoutubeDLRequest,
        onProgress: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLResponse = gate.download { YoutubeDL.getInstance().execute(request, null, onProgress) }

    /** Blocking: call off the main thread. Never throws. */
    fun update(appContext: Context): YtDlpUpdateResult = gate.update {
        try {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(appContext, YTDLP_CHANNEL)
            Log.i(TAG, "yt-dlp update: $status")
            toUpdateResult(status)
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed", e)
            YtDlpUpdateResult.Failed
        }
    }
}
