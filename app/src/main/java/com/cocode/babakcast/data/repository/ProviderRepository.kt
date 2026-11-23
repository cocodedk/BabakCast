package com.cocode.babakcast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cocode.babakcast.data.local.SecureStorage
import com.cocode.babakcast.data.model.Provider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val okHttpClient: okhttp3.OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }
    private val modelFetcher = AiModelFetcher(okHttpClient, json)

    private val providersDir = File(context.filesDir, "providers")
    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    val providers = _providers.asStateFlow()

    // SharedPreferences for model selection (not sensitive, doesn't need encryption)
    private val modelPrefs: SharedPreferences = context.getSharedPreferences(
        "provider_models",
        Context.MODE_PRIVATE
    )

    init {
        providersDir.mkdirs()
        loadProviders()
    }

    private fun loadProviders() {
        val loadedProviders = mutableListOf<Provider>()
        loadedProviders.addAll(getPredefinedProviders())
        loadedProviders.addAll(loadCustomProviders())
        _providers.value = loadedProviders
    }

    private fun getPredefinedProviders(): List<Provider> = PredefinedProviders.ALL

    private fun loadCustomProviders(): List<Provider> {
        val customProviders = mutableListOf<Provider>()
        providersDir.listFiles()?.forEach { file ->
            try {
                val jsonString = file.readText()
                val provider = json.decodeFromString<Provider>(jsonString)
                customProviders.add(provider)
            } catch (e: Exception) {
                // Skip invalid provider files
            }
        }
        return customProviders
    }

    suspend fun saveProvider(provider: Provider) {
        val file = File(providersDir, "${provider.id}.json")
        val jsonString = json.encodeToString(Provider.serializer(), provider)
        file.writeText(jsonString)
        loadProviders()
    }

    suspend fun deleteProvider(providerId: String) {
        if (getPredefinedProviders().any { it.id == providerId }) {
            return
        }
        val file = File(providersDir, "$providerId.json")
        if (file.exists()) {
            file.delete()
        }
        secureStorage.deleteApiKey(providerId)
        deleteSelectedModel(providerId)
        loadProviders()
    }

    fun getProvider(providerId: String): Provider? {
        return _providers.value.find { it.id == providerId }
    }

    fun hasApiKey(providerId: String): Boolean {
        return secureStorage.getApiKey(providerId) != null
    }

    fun getFirstProvider(): Provider? {
        return _providers.value.firstOrNull()
    }

    // Model selection methods

    /**
     * Get the selected model for a provider, or the default if none selected
     */
    fun getSelectedModel(providerId: String): String {
        val provider = getProvider(providerId) ?: return ""
        return modelPrefs.getString("model_$providerId", null) ?: provider.model
    }

    /**
     * Save the selected model for a provider
     */
    fun saveSelectedModel(providerId: String, model: String) {
        modelPrefs.edit()
            .putString("model_$providerId", model)
            .apply()
    }

    /**
     * Delete the selected model for a provider (revert to default)
     */
    fun deleteSelectedModel(providerId: String) {
        modelPrefs.edit()
            .remove("model_$providerId")
            .apply()
    }

    /**
     * Get provider with the currently selected model applied
     */
    fun getProviderWithSelectedModel(providerId: String): Provider? {
        val provider = getProvider(providerId) ?: return null
        val selectedModel = getSelectedModel(providerId)
        return provider.copy(model = selectedModel)
    }

    /**
     * Fetch list of model IDs for a provider. Dispatches to the provider-specific API.
     * For azure-openai returns empty (use static available_models). Others require API key for fetch.
     */
    suspend fun fetchModelsForProvider(providerId: String, apiKey: String?): Result<List<String>> =
        modelFetcher.fetchModelsForProvider(providerId, apiKey)
}
