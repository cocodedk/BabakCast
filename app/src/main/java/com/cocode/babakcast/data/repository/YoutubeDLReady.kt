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
 */
object YoutubeDLReady {

    sealed class YoutubeDLInitStatus {
        object Loading : YoutubeDLInitStatus()
        object Ready : YoutubeDLInitStatus()
        data class Failed(val message: String) : YoutubeDLInitStatus()
    }

    private val _status = MutableStateFlow<YoutubeDLInitStatus>(YoutubeDLInitStatus.Loading)
    val status: StateFlow<YoutubeDLInitStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Call from Application.onCreate(). Runs init on a background thread so it
     * doesn't block startup; updates [status] when done (Ready or Failed).
     */
    fun startInit(context: Context) {
        if (_status.value is YoutubeDLInitStatus.Ready) return
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                _status.value = YoutubeDLInitStatus.Ready
            } catch (e: Exception) {
                Log.e("YoutubeDLReady", "YoutubeDL init failed", e)
                // Build detailed message including cause chain for debugging
                val fullMessage = buildString {
                    append(e.message ?: e.javaClass.simpleName)
                    var cause = e.cause
                    while (cause != null) {
                        append(" → ")
                        append(cause.message ?: cause.javaClass.simpleName)
                        cause = cause.cause
                    }
                }
                Log.e("YoutubeDLReady", "Full error: $fullMessage")
                _status.value = YoutubeDLInitStatus.Failed(fullMessage)
            }
        }
    }
}
