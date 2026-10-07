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

/**
 * True when the app's versionCode differs from the one recorded at the last successful init,
 * which is the moment to compare the yt-dlp on disk with the one this build ships.
 *
 * youtubedl-android 0.18.1's init_ytdlp() only copies the bundled yt-dlp into the library's
 * own folder when that file is ABSENT: it never compares versions. So after an app update
 * the fdroid flavor would keep an older yt-dlp (one a replaced github build, or an older app
 * version, had put there), and a newer app that ships a newer yt-dlp would not get to use it.
 * [shouldResetYtdlp] decides what to do about the file; this only decides when to look. No
 * recorded versionCode ([UNSET_VERSION_CODE]) means "never recorded", which also counts as
 * changed.
 */
internal const val UNSET_VERSION_CODE = -1

internal fun versionCodeChanged(recordedVersionCode: Int, currentVersionCode: Int): Boolean =
    recordedVersionCode != currentVersionCode

/**
 * True when the daily yt-dlp refresh at start should run. Only the github flavor refreshes on
 * its own; the fdroid flavor never contacts GitHub at start, whatever the day.
 */
internal fun shouldRefreshAtStart(selfUpdate: Boolean, lastUpdateDay: Long, today: Long): Boolean =
    selfUpdate && lastUpdateDay != today

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
            // github self-updates yt-dlp at runtime, so an old extracted binary there is
            // expected to be replaced by refreshYoutubeDlIfDue(); only fdroid needs this.
            if (!BuildConfig.YTDLP_SELF_UPDATE) reconcileYtdlpAfterUpdate(appContext)
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
     * fdroid only (see [versionCodeChanged]): after an app update, keep the yt-dlp the user
     * installed from Settings when it is the same as or newer than the one this build ships,
     * and put the shipped one back (delete the directory, so init_ytdlp() re-extracts it)
     * when that one is newer or the installed file is broken. See [shouldResetYtdlp].
     */
    private fun reconcileYtdlpAfterUpdate(appContext: Context) {
        val prefs = appContext.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        val recorded = prefs.getInt(KEY_LAST_INIT_VERSION_CODE, UNSET_VERSION_CODE)
        if (!versionCodeChanged(recorded, BuildConfig.VERSION_CODE)) return
        resetYtdlpIfNeeded(ytDlpDir(appContext), shippedYtdlpVersion(appContext))
    }

    /** The version of the yt-dlp that ships inside youtubedl-android, or null when unreadable. */
    private fun shippedYtdlpVersion(appContext: Context): String? = try {
        appContext.resources.openRawResource(com.yausername.youtubedl_android.R.raw.ytdlp)
            .use(YtDlpVersion::of)
    } catch (e: Exception) {
        null
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
     * Gated on [BuildConfig.YTDLP_SELF_UPDATE]: the fdroid flavor never updates yt-dlp on its
     * own. It keeps the yt-dlp inside youtubedl-android until the user taps "Update yt-dlp" in
     * Settings (see [YtDlpUpdater]).
     */
    private fun refreshYoutubeDlIfDue(appContext: Context) {
        try {
            val prefs = appContext.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            val today = System.currentTimeMillis() / MILLIS_PER_DAY
            val lastDay = prefs.getLong(KEY_LAST_UPDATE_DAY, 0L)
            if (!shouldRefreshAtStart(BuildConfig.YTDLP_SELF_UPDATE, lastDay, today)) return
            val result = YoutubeDL.getInstance().updateYoutubeDL(appContext, YTDLP_CHANNEL)
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
