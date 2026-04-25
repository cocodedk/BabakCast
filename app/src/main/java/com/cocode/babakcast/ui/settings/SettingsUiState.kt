package com.cocode.babakcast.ui.settings

import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.model.SummaryLength

data class SettingsUiState(
    val providers: List<ProviderState> = emptyList(),
    val selectedProvider: Provider? = null,
    val editingApiKey: String = "",
    val editingApiUrl: String = "",
    val editingModel: String = "",
    val showModelDropdown: Boolean = false,
    val showProviderDialog: Boolean = false,
    val fetchedModels: List<String> = emptyList(),
    val fetchedModelsProviderId: String? = null,
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val defaultLanguage: String = "en",
    val adaptiveSummaryLength: Boolean = true,
    val defaultSummaryLength: SummaryLength = SummaryLength.MEDIUM,
    val autoPlayNext: Boolean = false,
    val updateState: UpdateUiState = UpdateUiState.Idle
)
