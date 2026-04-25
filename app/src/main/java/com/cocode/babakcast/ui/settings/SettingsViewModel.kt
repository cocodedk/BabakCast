package com.cocode.babakcast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.local.SecureStorage
import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.repository.ProviderRepository
import com.cocode.babakcast.data.repository.UpdateRepository
import com.cocode.babakcast.domain.network.NetworkType
import com.cocode.babakcast.domain.network.NetworkTypeProvider
import com.cocode.babakcast.domain.update.UpdateAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val secureStorage: SecureStorage,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val networkTypeProvider: NetworkTypeProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _downloadEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val downloadEvents: SharedFlow<String> = _downloadEvents.asSharedFlow()

    init {
        loadProviders()
        observeSettings()
    }

    private fun loadProviders() {
        viewModelScope.launch {
            providerRepository.providers.collect { providers ->
                val providerStates = providers.map { provider ->
                    val selectedModel = providerRepository.getSelectedModel(provider.id)
                    ProviderState(
                        provider = provider,
                        hasApiKey = secureStorage.getApiKey(provider.id) != null,
                        maskedApiKey = secureStorage.maskApiKey(secureStorage.getApiKey(provider.id)),
                        selectedModel = selectedModel
                    )
                }
                _uiState.value = _uiState.value.copy(providers = providerStates)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    defaultLanguage = settings.defaultLanguage,
                    adaptiveSummaryLength = settings.adaptiveSummaryLength,
                    defaultSummaryLength = settings.defaultSummaryLength,
                    autoPlayNext = settings.autoPlayNext
                )
            }
        }
    }

    fun updateDefaultLanguage(language: String) {
        _uiState.value = _uiState.value.copy(defaultLanguage = language)
        viewModelScope.launch {
            settingsRepository.updateDefaultLanguage(language.trim())
        }
    }

    fun updateAdaptiveSummaryLength(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(adaptiveSummaryLength = enabled)
        viewModelScope.launch {
            settingsRepository.updateAdaptiveSummaryLength(enabled)
        }
    }

    fun updateDefaultSummaryLength(length: SummaryLength) {
        _uiState.value = _uiState.value.copy(defaultSummaryLength = length)
        viewModelScope.launch {
            settingsRepository.updateDefaultSummaryLength(length)
        }
    }

    fun updateAutoPlayNext(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoPlayNext = enabled)
        viewModelScope.launch {
            settingsRepository.updateAutoPlayNext(enabled)
        }
    }

    fun selectProvider(provider: Provider) {
        val currentApiKey = secureStorage.getApiKey(provider.id) ?: ""
        val selectedModel = providerRepository.getSelectedModel(provider.id)
        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            editingApiKey = currentApiKey,
            editingApiUrl = provider.api_base_url,
            editingModel = selectedModel,
            showModelDropdown = false,
            showProviderDialog = true,
            modelsLoading = false,
            modelsError = null
        )
        fetchModelsForProvider(provider.id, currentApiKey)
    }

    private fun fetchModelsForProvider(providerId: String, apiKey: String?) {
        _uiState.value = _uiState.value.copy(modelsLoading = true, modelsError = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                providerRepository.fetchModelsForProvider(providerId, apiKey.takeIf { !it.isNullOrBlank() })
            }
            _uiState.value = _uiState.value.copy(
                modelsLoading = false,
                fetchedModels = result.getOrElse { emptyList() },
                fetchedModelsProviderId = providerId,
                modelsError = result.exceptionOrNull()?.message
            )
        }
    }

    /**
     * Models to show in the provider config dialog.
     * Uses the fetched list when it matches the provider; otherwise falls back to provider.available_models.
     */
    fun getModelsForProvider(provider: Provider): List<String> {
        val state = _uiState.value
        return if (provider.id == state.fetchedModelsProviderId && state.fetchedModels.isNotEmpty()) {
            state.fetchedModels
        } else {
            provider.available_models
        }
    }

    fun dismissProviderDialog() {
        _uiState.value = _uiState.value.copy(
            selectedProvider = null,
            editingApiKey = "",
            editingApiUrl = "",
            editingModel = "",
            showModelDropdown = false,
            showProviderDialog = false
        )
    }

    fun updateEditingApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(editingApiKey = apiKey)
    }

    fun updateEditingApiUrl(apiUrl: String) {
        _uiState.value = _uiState.value.copy(editingApiUrl = apiUrl)
    }

    fun updateEditingModel(model: String) {
        _uiState.value = _uiState.value.copy(
            editingModel = model,
            showModelDropdown = false
        )
    }

    fun toggleModelDropdown() {
        _uiState.value = _uiState.value.copy(
            showModelDropdown = !_uiState.value.showModelDropdown
        )
    }

    fun dismissModelDropdown() {
        _uiState.value = _uiState.value.copy(showModelDropdown = false)
    }

    fun saveProviderConfig() {
        val provider = _uiState.value.selectedProvider ?: return
        val apiKey = _uiState.value.editingApiKey
        val model = _uiState.value.editingModel

        viewModelScope.launch {
            // Save API key
            if (apiKey.isNotBlank()) {
                secureStorage.saveApiKey(provider.id, apiKey)
                settingsRepository.updateDefaultProvider(provider.id)
            } else {
                secureStorage.deleteApiKey(provider.id)
                val currentDefault = settingsRepository.settings.first().defaultProviderId
                if (currentDefault == provider.id) {
                    val fallback = providerRepository.providers.value.firstOrNull {
                        secureStorage.getApiKey(it.id) != null
                    }?.id
                    settingsRepository.updateDefaultProvider(fallback)
                }
            }

            // Save selected model
            if (model.isNotBlank() && model != provider.model) {
                providerRepository.saveSelectedModel(provider.id, model)
            }

            refreshProviderStates()
            dismissProviderDialog()
        }
    }

    fun deleteProviderApiKey(providerId: String) {
        viewModelScope.launch {
            secureStorage.deleteApiKey(providerId)
            providerRepository.deleteSelectedModel(providerId)
            val currentDefault = settingsRepository.settings.first().defaultProviderId
            if (currentDefault == providerId) {
                val fallback = providerRepository.providers.value.firstOrNull {
                    secureStorage.getApiKey(it.id) != null
                }?.id
                settingsRepository.updateDefaultProvider(fallback)
            }
            refreshProviderStates()
            dismissProviderDialog()
        }
    }

    fun checkForUpdate() {
        if (_uiState.value.updateState is UpdateUiState.Checking) return
        _uiState.value = _uiState.value.copy(updateState = UpdateUiState.Checking)
        viewModelScope.launch {
            val newState = when (val result = updateRepository.checkForUpdate()) {
                is UpdateAvailability.UpToDate -> UpdateUiState.UpToDate(result.installed)
                is UpdateAvailability.CheckFailed -> UpdateUiState.Failed(result.reason)
                is UpdateAvailability.UpdateAvailable -> {
                    val networkType = networkTypeProvider.current()
                    UpdateUiState.Available(
                        installed = result.installed,
                        latest = result.latest,
                        apkDownloadUrl = result.apkDownloadUrl,
                        apkSizeBytes = result.apkSizeBytes,
                        networkType = networkType,
                        awaitingCellularConfirm = networkType == NetworkType.CELLULAR
                    )
                }
            }
            _uiState.value = _uiState.value.copy(updateState = newState)
        }
    }

    fun requestDownload() {
        val state = _uiState.value.updateState as? UpdateUiState.Available ?: return
        val current = networkTypeProvider.current()
        if (current == NetworkType.CELLULAR && state.awaitingCellularConfirm) {
            _uiState.value = _uiState.value.copy(updateState = state.copy(networkType = current))
            return
        }
        _downloadEvents.tryEmit(state.apkDownloadUrl)
    }

    fun confirmCellularDownload() {
        val state = _uiState.value.updateState as? UpdateUiState.Available ?: return
        val current = networkTypeProvider.current()
        _uiState.value = _uiState.value.copy(
            updateState = state.copy(awaitingCellularConfirm = false, networkType = current)
        )
        _downloadEvents.tryEmit(state.apkDownloadUrl)
    }

    fun dismissUpdatePrompt() {
        _uiState.value = _uiState.value.copy(updateState = UpdateUiState.Idle)
    }

    private fun refreshProviderStates() {
        val providers = _uiState.value.providers.map { state ->
            val selectedModel = providerRepository.getSelectedModel(state.provider.id)
            state.copy(
                hasApiKey = secureStorage.getApiKey(state.provider.id) != null,
                maskedApiKey = secureStorage.maskApiKey(secureStorage.getApiKey(state.provider.id)),
                selectedModel = selectedModel
            )
        }
        _uiState.value = _uiState.value.copy(providers = providers)
    }
}
