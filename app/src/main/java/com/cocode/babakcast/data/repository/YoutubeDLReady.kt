package com.cocode.babakcast.data.repository

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            try {
                YoutubeDL.getInstance().init(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "YoutubeDL init failed", e)
                _status.value = YoutubeDLInitStatus.Failed(describeCauseChain(e))
                return@launch
            }
            refreshYoutubeDlIfDue(appContext)
            _status.value = YoutubeDLInitStatus.Ready
        }
    }

    /**
     * Refresh the bundled yt-dlp to the latest nightly at most once per calendar day.
     * Best-effort: on any failure (offline, GitHub unreachable) the bundled binary is
     * kept and the check is retried on the next launch.
     */
    private fun refreshYoutubeDlIfDue(appContext: Context) {
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
