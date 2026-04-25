package com.cocode.babakcast.ui.settings

import com.cocode.babakcast.domain.network.NetworkType
import com.cocode.babakcast.domain.update.AppVersion

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpToDate(val installed: AppVersion) : UpdateUiState()
    data class Available(
        val installed: AppVersion,
        val latest: AppVersion,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long,
        val networkType: NetworkType,
        val awaitingCellularConfirm: Boolean
    ) : UpdateUiState()
    data class Failed(val reason: String) : UpdateUiState()
}
