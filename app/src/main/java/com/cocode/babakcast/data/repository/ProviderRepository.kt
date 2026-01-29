package com.cocode.babakcast.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cocode.babakcast.data.local.SecureStorage
import com.cocode.babakcast.data.model.AuthConfig
import com.cocode.babakcast.data.model.LimitConfig
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.model.RequestConfig
import com.cocode.babakcast.data.model.ResponseConfig
import com.cocode.babakcast.data.remote.AnthropicModelsResponse
import com.cocode.babakcast.data.remote.GeminiModelsResponse
import com.cocode.babakcast.data.remote.OpenAIModelsResponse
import com.cocode.babakcast.data.remote.OpenRouterModelsResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
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
                available_models = emptyList(), // Fetched from OpenAI API when configuring
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
                available_models = emptyList(), // Fetched from Anthropic API when configuring
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
                available_models = emptyList(), // Fetched from Gemini API when configuring
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
                available_models = emptyList(), // Fetched from OpenRouter API when configuring
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

    /**
     * Fetch list of model IDs for a provider. Dispatches to the provider-specific API.
     * For azure-openai returns empty (use static available_models). Others require API key for fetch.
     */
    suspend fun fetchModelsForProvider(providerId: String, apiKey: String?): Result<List<String>> = when (providerId) {
        "openai" -> fetchOpenAIModels(apiKey)
        "anthropic" -> fetchAnthropicModels(apiKey)
        "google-gemini" -> fetchGeminiModels(apiKey)
        "openrouter" -> fetchOpenRouterModels(apiKey)
        else -> Result.success(emptyList()) // Azure and custom: use static list
    }

    /** GET https://api.openai.com/v1/models — requires Bearer API key */
    private suspend fun fetchOpenAIModels(apiKey: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) return@withContext Result.failure(Exception("OpenAI API key required to list models"))
        try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("OpenAI models API failed: ${response.code} - $body"))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val parsed = json.decodeFromString<OpenAIModelsResponse>(body)
            val ids = parsed.data.map { it.id }.sorted()
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET https://api.anthropic.com/v1/models — requires x-api-key and anthropic-version */
    private suspend fun fetchAnthropicModels(apiKey: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) return@withContext Result.failure(Exception("Anthropic API key required to list models"))
        try {
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/models?limit=1000")
                .get()
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("Anthropic models API failed: ${response.code} - $body"))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val parsed = json.decodeFromString<AnthropicModelsResponse>(body)
            val ids = parsed.data.map { it.id }.sorted()
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET https://generativelanguage.googleapis.com/v1beta/models?key=... — API key as query param */
    private suspend fun fetchGeminiModels(apiKey: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) return@withContext Result.failure(Exception("Gemini API key required to list models"))
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("Gemini models API failed: ${response.code} - $body"))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val parsed = json.decodeFromString<GeminiModelsResponse>(body)
            val models = parsed.models ?: emptyList()
            // API returns "models/gemini-2.0-flash"; we need "gemini-2.0-flash" for generateContent
            val ids = models.map { it.name.removePrefix("models/") }.sorted()
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** GET https://openrouter.ai/api/v1/models — API key optional */
    private suspend fun fetchOpenRouterModels(apiKey: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .get()
            if (!apiKey.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("OpenRouter models API failed: ${response.code} - $body"))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val parsed = json.decodeFromString<OpenRouterModelsResponse>(body)
            val ids = parsed.data.map { it.id }.sorted()
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
