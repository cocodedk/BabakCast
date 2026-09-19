package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.cocode.babakcast.BuildConfig
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * True when the yt-dlp binary that youtubedl-android already extracted should be deleted
 * before init, so its own init_ytdlp() re-extracts the copy bundled with this build.
 *
 * youtubedl-android 0.18.1's init_ytdlp() only copies the raw resource into
 * <noBackupFilesDir>/youtubedl-android/yt-dlp/yt-dlp when that file is ABSENT — it never
 * compares versions. Left alone, the fdroid flavor (which never calls updateYoutubeDL)
 * would keep running whichever yt-dlp got extracted by an older app version, or by a
 * github-flavor install this one replaced. A plain versionCode mismatch is enough to
 * decide "stale, re-extract": it catches both an app update carrying a newer bundled
 * yt-dlp, and an install that replaces a self-updating github build with this one.
 * No recorded versionCode ([UNSET_VERSION_CODE]) means "never recorded" — that still
 * compares unequal, but a delete on the not-yet-created directory is a harmless no-op.
 */
internal const val UNSET_VERSION_CODE = -1

internal fun shouldReExtractYtdlp(recordedVersionCode: Int, currentVersionCode: Int): Boolean =
    recordedVersionCode != currentVersionCode

/**
 * Tracks YoutubeDL initialization. Start from Application.onCreate();
 * UI can observe [status] and show "Preparing..." until [YoutubeDLInitStatus.Ready].
 *
 * The bundled yt-dlp binary is frozen at whatever version ships with youtubedl-android,
 * which YouTube's server-side changes eventually reject (HTTP 403 / extraction
 * failures). [startInit] therefore refreshes yt-dlp to the latest nightly at most once
 * per day, BEFORE reporting [YoutubeDLInitStatus.Ready]. Doing it before Ready is
 * required for safety: YoutubeDL.execute() is not synchronized against the updater, so
 * a download must never overlap the binary swap — gating downloads on Ready guarantees
 * that. The refresh is best-effort; any failure keeps the bundled binary so downloads
 * still work offline.
 */
object YoutubeDLReady {

    private const val TAG = "YoutubeDLReady"
    private const val UPDATE_PREFS = "ytdlp_update"
    private const val KEY_LAST_UPDATE_DAY = "last_update_day"
    private const val KEY_LAST_INIT_VERSION_CODE = "last_init_version_code"
    private const val MILLIS_PER_DAY = 86_400_000L

    sealed class YoutubeDLInitStatus {
        object Loading : YoutubeDLInitStatus()
        object Ready : YoutubeDLInitStatus()
        data class Failed(val message: String) : YoutubeDLInitStatus()
    }

    private val _status = MutableStateFlow<YoutubeDLInitStatus>(YoutubeDLInitStatus.Loading)
    val status: StateFlow<YoutubeDLInitStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Call from Application.onCreate(). Runs init and a throttled yt-dlp refresh on a
     * background thread so it doesn't block startup; updates [status] when done.
     */
    fun startInit(context: Context) {
        if (_status.value is YoutubeDLInitStatus.Ready) return
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            // github self-updates yt-dlp at runtime, so a stale extracted binary there is
            // expected to be replaced by refreshYoutubeDlIfDue(); only fdroid needs this.
            if (!BuildConfig.YTDLP_SELF_UPDATE) reExtractYtdlpIfStale(appContext)
            try {
                YoutubeDL.getInstance().init(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "YoutubeDL init failed", e)
                _status.value = YoutubeDLInitStatus.Failed(describeCauseChain(e))
                return@launch
            }
            if (!BuildConfig.YTDLP_SELF_UPDATE) recordYtdlpInitVersion(appContext)
            initFFmpeg(appContext)
            refreshYoutubeDlIfDue(appContext)
            _status.value = YoutubeDLInitStatus.Ready
        }
    }

    /**
     * fdroid only (see [shouldReExtractYtdlp]): delete the yt-dlp youtubedl-android already
     * extracted when it doesn't match this build's versionCode, so init_ytdlp() re-extracts
     * the one bundled here instead of silently keeping an older binary.
     */
    private fun reExtractYtdlpIfStale(appContext: Context) {
        val prefs = appContext.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        val recorded = prefs.getInt(KEY_LAST_INIT_VERSION_CODE, UNSET_VERSION_CODE)
        if (!shouldReExtractYtdlp(recorded, BuildConfig.VERSION_CODE)) return
        // Matches youtubedl-android's own baseDir/"yt-dlp" layout (YoutubeDL.init()/
        // init_ytdlp() in library 0.18.1); deleting the directory is what that library
        // itself does on a failed extraction, so init_ytdlp() starts from a clean slate.
        File(File(appContext.noBackupFilesDir, "youtubedl-android"), "yt-dlp").deleteRecursively()
    }

    /** fdroid only: called after a successful init, so a failed one is retried next launch. */
    private fun recordYtdlpInitVersion(appContext: Context) {
        appContext.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_INIT_VERSION_CODE, BuildConfig.VERSION_CODE).apply()
    }

    /**
     * Unpack yt-dlp's own ffmpeg, which it shells out to whenever a download falls through to
     * a separate video + audio pair — see DOWNLOAD_FORMAT. It is a different binary from the
     * FFmpegKit used for splitting, and is not unpacked unless initialised here.
     *
     * Best-effort on purpose: only the fallback path needs it, so a failure here must not gate
     * the muxed downloads that never merge.
     */
    private fun initFFmpeg(appContext: Context) {
        try {
            FFmpeg.getInstance().init(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "ffmpeg init failed; merged video+audio downloads will not work", e)
        }
    }

    /**
     * Refresh the bundled yt-dlp to the latest nightly at most once per calendar day.
     * Best-effort: on any failure (offline, GitHub unreachable) the bundled binary is
     * kept and the check is retried on the next launch.
     *
     * Gated on [BuildConfig.YTDLP_SELF_UPDATE]: the fdroid flavor must never download and
     * run a binary at runtime, so it keeps whatever yt-dlp ships inside youtubedl-android.
     */
    private fun refreshYoutubeDlIfDue(appContext: Context) {
        if (!BuildConfig.YTDLP_SELF_UPDATE) return
        try {
            val prefs = appContext.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            val today = System.currentTimeMillis() / MILLIS_PER_DAY
            if (prefs.getLong(KEY_LAST_UPDATE_DAY, 0L) == today) return
            val result = YoutubeDL.getInstance()
                .updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.NIGHTLY)
            prefs.edit().putLong(KEY_LAST_UPDATE_DAY, today).apply()
            Log.i(TAG, "yt-dlp refresh: $result")
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp refresh skipped; using bundled binary", e)
        }
    }

    /** Flattens an exception's cause chain into a single readable message. */
    private fun describeCauseChain(e: Throwable): String = buildString {
        append(e.message ?: e.javaClass.simpleName)
        var cause = e.cause
        while (cause != null) {
            append(" → ")
            append(cause.message ?: cause.javaClass.simpleName)
            cause = cause.cause
        }
    }
}
