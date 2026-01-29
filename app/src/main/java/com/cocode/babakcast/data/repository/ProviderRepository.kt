package com.cocode.babakcast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cocode.babakcast.data.local.SecureStorage
import com.cocode.babakcast.data.model.AuthConfig
import com.cocode.babakcast.data.model.LimitConfig
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.model.RequestConfig
import com.cocode.babakcast.data.model.ResponseConfig
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
    private val secureStorage: SecureStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

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

    /**
     * Get predefined providers with available models
     */
    private fun getPredefinedProviders(): List<Provider> {
        return listOf(
            Provider(
                id = "openai",
                display_name = "OpenAI",
                api_base_url = "https://api.openai.com/v1/chat/completions",
                auth = AuthConfig(
                    type = "bearer",
                    header = "Authorization",
                    prefix = "Bearer "
                ),
                model = "gpt-4o-mini",
                available_models = listOf(
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-4-turbo",
                    "gpt-4",
                    "gpt-3.5-turbo",
                    "o1-preview",
                    "o1-mini"
                ),
                request = RequestConfig(
                    type = "chat",
                    messages_path = "messages",
                    temperature_path = "temperature",
                    max_tokens_path = "max_tokens"
                ),
                response = ResponseConfig(
                    content_path = "choices[0].message.content"
                ),
                limits = LimitConfig(
                    max_context_tokens = 128000,
                    max_output_tokens = 4096
                )
            ),
            Provider(
                id = "azure-openai",
                display_name = "Azure OpenAI",
                api_base_url = "https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT/chat/completions?api-version=2024-02-15-preview",
                auth = AuthConfig(
                    type = "api_key",
                    header = "api-key",
                    prefix = ""
                ),
                model = "gpt-4",
                available_models = listOf(
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-4-turbo",
                    "gpt-4",
                    "gpt-35-turbo"
                ),
                request = RequestConfig(
                    type = "chat",
                    messages_path = "messages",
                    temperature_path = "temperature",
                    max_tokens_path = "max_tokens"
                ),
                response = ResponseConfig(
                    content_path = "choices[0].message.content"
                ),
                limits = LimitConfig(
                    max_context_tokens = 128000,
                    max_output_tokens = 4096
                )
            ),
            Provider(
                id = "anthropic",
                display_name = "Anthropic",
                api_base_url = "https://api.anthropic.com/v1/messages",
                auth = AuthConfig(
                    type = "api_key",
                    header = "x-api-key",
                    prefix = ""
                ),
                model = "claude-3-5-sonnet-20241022",
                available_models = listOf(
                    "claude-3-5-sonnet-20241022",
                    "claude-3-5-haiku-20241022",
                    "claude-3-opus-20240229",
                    "claude-3-sonnet-20240229",
                    "claude-3-haiku-20240307"
                ),
                request = RequestConfig(
                    type = "chat",
                    messages_path = "messages",
                    temperature_path = "temperature",
                    max_tokens_path = "max_tokens"
                ),
                response = ResponseConfig(
                    content_path = "content[0].text"
                ),
                limits = LimitConfig(
                    max_context_tokens = 200000,
                    max_output_tokens = 8192
                )
            ),
            Provider(
                id = "google-gemini",
                display_name = "Google Gemini",
                api_base_url = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
                auth = AuthConfig(
                    type = "api_key",
                    header = "x-goog-api-key",
                    prefix = ""
                ),
                model = "gemini-1.5-flash",
                available_models = listOf(
                    "gemini-2.0-flash-exp",
                    "gemini-1.5-pro",
                    "gemini-1.5-flash",
                    "gemini-1.5-flash-8b",
                    "gemini-1.0-pro"
                ),
                request = RequestConfig(
                    type = "chat",
                    messages_path = "contents",
                    temperature_path = "generationConfig.temperature",
                    max_tokens_path = "generationConfig.maxOutputTokens"
                ),
                response = ResponseConfig(
                    content_path = "candidates[0].content.parts[0].text"
                ),
                limits = LimitConfig(
                    max_context_tokens = 1000000,
                    max_output_tokens = 8192
                )
            ),
            Provider(
                id = "openrouter",
                display_name = "OpenRouter",
                api_base_url = "https://openrouter.ai/api/v1/chat/completions",
                auth = AuthConfig(
                    type = "bearer",
                    header = "Authorization",
                    prefix = "Bearer "
                ),
                model = "openai/gpt-4o-mini",
                available_models = listOf(
                    "openai/gpt-4o",
                    "openai/gpt-4o-mini",
                    "anthropic/claude-3.5-sonnet",
                    "anthropic/claude-3-haiku",
                    "google/gemini-pro-1.5",
                    "google/gemini-flash-1.5",
                    "meta-llama/llama-3.1-70b-instruct",
                    "meta-llama/llama-3.1-8b-instruct",
                    "mistralai/mistral-large",
                    "deepseek/deepseek-chat"
                ),
                request = RequestConfig(
                    type = "chat",
                    messages_path = "messages",
                    temperature_path = "temperature",
                    max_tokens_path = "max_tokens"
                ),
                response = ResponseConfig(
                    content_path = "choices[0].message.content"
                ),
                limits = LimitConfig(
                    max_context_tokens = 128000,
                    max_output_tokens = 4096
                )
            )
        )
    }

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
}
