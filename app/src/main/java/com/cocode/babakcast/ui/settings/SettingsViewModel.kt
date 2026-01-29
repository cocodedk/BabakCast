package com.cocode.babakcast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.local.SecureStorage
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
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
            } else {
                secureStorage.deleteApiKey(provider.id)
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
            refreshProviderStates()
            dismissProviderDialog()
        }
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
    val modelsError: String? = null
)

data class ProviderState(
    val provider: Provider,
    val hasApiKey: Boolean,
    val maskedApiKey: String,
    val selectedModel: String
)
