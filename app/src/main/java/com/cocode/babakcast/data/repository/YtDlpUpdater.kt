package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import okhttp3.OkHttpClient

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
 * Keeps a yt-dlp update and a yt-dlp run from overlapping, and never makes either wait for the
 * other: whichever comes second is refused at once. Runs share the read side. An update takes
 * the write side, and a run that starts meanwhile fails with [AppError.ToolUpdating] (the
 * screen then says to try again) instead of queueing behind a network request.
 */
internal class YtDlpGate {
    private val lock = ReentrantReadWriteLock()

    fun <T> run(block: () -> T): T {
        if (!lock.readLock().tryLock()) {
            throw AppErrorException(AppError.ToolUpdating(), "yt-dlp is being updated")
        }
        try {
            return block()
        } finally {
            lock.readLock().unlock()
        }
    }

    fun update(perform: () -> YtDlpUpdateResult): YtDlpUpdateResult {
        if (!lock.writeLock().tryLock()) return YtDlpUpdateResult.DownloadRunning
        try {
            return perform()
        } finally {
            lock.writeLock().unlock()
        }
    }
}

/** Where the library keeps the yt-dlp it runs: <noBackupFilesDir>/youtubedl-android/yt-dlp/yt-dlp. */
internal fun ytDlpDir(appContext: Context): File =
    File(File(appContext.noBackupFilesDir, "youtubedl-android"), "yt-dlp")

internal fun ytDlpBinary(appContext: Context): File = File(ytDlpDir(appContext), "yt-dlp")

/**
 * The yt-dlp update a user starts from Settings (the fdroid flavour never updates on its own),
 * and the one gate every yt-dlp run goes through. X's direct downloads (XDirectDownloader) do
 * not run yt-dlp, so they do not use it.
 */
object YtDlpUpdater {
    private const val TAG = "YtDlpUpdater"

    private val gate = YtDlpGate()

    /** Runs yt-dlp. Test seam: the real library call by default. */
    internal var runner: (YoutubeDLRequest, ((Float, Long, String) -> Unit)?) -> YoutubeDLResponse =
        { request, onProgress -> YoutubeDL.getInstance().execute(request, null, onProgress) }

    /** Every yt-dlp run goes through here, so an update cannot overlap one. */
    internal fun execute(
        request: YoutubeDLRequest,
        onProgress: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLResponse = gate.run { runner(request, onProgress) }

    /** Blocking, and over within about a minute: call off the main thread. Never throws. */
    fun update(appContext: Context): YtDlpUpdateResult =
        update(YtDlpInstaller(OkHttpClient(), YTDLP_CHANNEL.apiUrl, ytDlpBinary(appContext)))

    internal fun update(installer: YtDlpInstaller): YtDlpUpdateResult = gate.update {
        try {
            installer.update().also { Log.i(TAG, "yt-dlp update: $it") }
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed", e)
            YtDlpUpdateResult.Failed
        }
    }
}
