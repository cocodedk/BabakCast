package com.cocode.babakcast.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.repository.YtDlpUpdateResult
import com.cocode.babakcast.data.repository.YtDlpUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface DownloadToolState {
    data object Idle : DownloadToolState
    data object Running : DownloadToolState
    data class Finished(val result: YtDlpUpdateResult) : DownloadToolState
}

/** Runs the yt-dlp update the user starts from Settings (fdroid flavour only). */
@HiltViewModel
class DownloadToolViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<DownloadToolState>(DownloadToolState.Idle)
    val state: StateFlow<DownloadToolState> = _state.asStateFlow()

    fun update() {
        if (_state.value == DownloadToolState.Running) return
        _state.value = DownloadToolState.Running
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { YtDlpUpdater.update(context) }
            _state.value = DownloadToolState.Finished(result)
        }
    }
}
