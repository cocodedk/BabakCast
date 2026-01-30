package com.cocode.babakcast.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.repository.YouTubeRepository
import com.cocode.babakcast.util.DownloadFileParser
import com.cocode.babakcast.util.ShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val shareHelper: ShareHelper,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
        observeSettings()
    }

    fun refreshDownloads() {
        loadDownloads()
    }

    fun clearDownloads() {
        if (_uiState.value.isCleaningDownloads) return
        _uiState.value = _uiState.value.copy(
            isCleaningDownloads = true,
            message = null
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { youtubeRepository.cleanupVideos() }
            }

            _uiState.value = _uiState.value.copy(
                isCleaningDownloads = false,
                message = if (result.isSuccess) {
                    "Downloads cleared"
                } else {
                    "Failed to clear downloads: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                }
            )
            loadDownloads()
        }
    }

    fun shareDownload(item: DownloadItem) {
        val missing = item.files.firstOrNull { !it.exists() }
        if (missing != null) {
            _uiState.value = _uiState.value.copy(message = "File not found")
            return
        }
        shareHelper.shareFiles(item.files, "video/*")
    }

    fun deleteDownload(item: DownloadItem) {
        _uiState.value = _uiState.value.copy(message = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    item.files.all { file ->
                        if (file.exists()) {
                            file.delete()
                        } else {
                            true
                        }
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess && result.getOrDefault(false)) {
                    "Download deleted"
                } else {
                    "Failed to delete download"
                }
            )
            loadDownloads()
        }
    }

    private fun loadDownloads() {
        _uiState.value = _uiState.value.copy(isLoadingDownloads = true, downloadsError = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { youtubeRepository.listDownloads() }
            }
            _uiState.value = _uiState.value.copy(
                isLoadingDownloads = false,
                downloads = buildDownloadItems(result.getOrElse { emptyList() }),
                downloadsError = result.exceptionOrNull()?.message
            )
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(autoPlayNext = settings.autoPlayNext)
            }
        }
    }

    private fun buildDownloadItems(files: List<java.io.File>): List<DownloadItem> {
        val groups = files.groupBy { file ->
            val base = file.name.substringBeforeLast(".")
            DownloadFileParser.extractGroupKey(base)
        }

        return groups.entries
            .map { (groupKey, groupFiles) ->
                val sortedFiles = groupFiles.sortedWith(compareBy<java.io.File> { file ->
                    val base = file.name.substringBeforeLast(".")
                    DownloadFileParser.extractPartNumber(base) ?: Int.MAX_VALUE
                }.thenBy { it.name })

                val totalSize = groupFiles.sumOf { it.length() }
                val lastModified = groupFiles.maxOfOrNull { it.lastModified() } ?: 0L

                DownloadItem(
                    displayName = humanizeGroupName(groupKey),
                    files = sortedFiles,
                    sizeBytes = totalSize,
                    lastModified = lastModified,
                    partCount = groupFiles.size
                )
            }
            .sortedByDescending { it.lastModified }
    }

    private fun humanizeGroupName(groupKey: String): String {
        val idMatch = Regex("(.+)[_-]([A-Za-z0-9_-]{11})$").find(groupKey)
        val withoutId = idMatch?.groupValues?.get(1) ?: groupKey
        val cleaned = withoutId
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { groupKey }
        return cleaned
    }
}

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val isLoadingDownloads: Boolean = false,
    val downloadsError: String? = null,
    val isCleaningDownloads: Boolean = false,
    val message: String? = null,
    val autoPlayNext: Boolean = false
)

data class DownloadItem(
    val displayName: String,
    val files: List<java.io.File>,
    val sizeBytes: Long,
    val lastModified: Long,
    val partCount: Int
)
