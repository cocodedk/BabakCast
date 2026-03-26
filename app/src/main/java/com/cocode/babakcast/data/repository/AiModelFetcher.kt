package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.remote.AnthropicModelsResponse
import com.cocode.babakcast.data.remote.GeminiModelsResponse
import com.cocode.babakcast.data.remote.OpenAIModelsResponse
import com.cocode.babakcast.data.remote.OpenRouterModelsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the list of available models from each AI provider API.
 *
 * This is an internal implementation detail of [ProviderRepository].
 */
internal class AiModelFetcher(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
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
