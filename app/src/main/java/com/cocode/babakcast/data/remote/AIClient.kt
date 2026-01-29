package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.AIMessage
import com.cocode.babakcast.data.model.AIRequest
import com.cocode.babakcast.data.model.AIResponse
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider-agnostic AI HTTP client
 * Handles request building and response parsing based on provider schema
 */
@Singleton
class AIClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val providerRepository: ProviderRepository,
    private val secureStorage: com.cocode.babakcast.data.local.SecureStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

    /**
     * Make AI request to provider
     */
    suspend fun makeRequest(
        provider: Provider,
        messages: List<AIMessage>,
        temperature: Double,
        maxTokens: Int
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        try {
            // Get API key
            val apiKey = secureStorage.getApiKey(provider.id)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("API key not found for provider: ${provider.display_name}")
                )

            // Build request body
            val requestBody = buildRequest(provider, messages, temperature, maxTokens)

            // Build HTTP request
            val url = provider.api_base_url.replace("{model}", provider.model)
            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader(provider.auth.header, "${provider.auth.prefix}$apiKey")
                .build()

            // Execute request
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    IOException("API request failed: ${response.code} - $errorBody")
                )
            }

            // Parse response
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val aiResponse = parseResponse(provider, responseBody)
            Result.success(aiResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Build request JSON based on provider schema
     */
    private fun buildRequest(
        provider: Provider,
        messages: List<AIMessage>,
        temperature: Double,
        maxTokens: Int
    ): String {
        val jsonObject = buildJsonObject {
            // Add messages
            val messagesArray: List<JsonObject> = messages.map { msg ->
                buildJsonObject {
                    put("role", msg.role)
                    put("content", msg.content)
                }
            }
            put(provider.request.messages_path, JsonArray(messagesArray.map { it as JsonElement }))

            // Add temperature
            put(provider.request.temperature_path, temperature)

            // Add max tokens
            put(provider.request.max_tokens_path, maxTokens)

            // Add model if needed (some providers need it in body)
            if (provider.request.type == "chat") {
                put("model", provider.model)
            }
        }

        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    /**
     * Parse response using JSON path from provider schema
     */
    private fun parseResponse(provider: Provider, responseBody: String): AIResponse {
        val jsonObject = json.parseToJsonElement(responseBody).jsonObject
        
        // Extract content using path (simple implementation for common paths)
        val content = extractContent(jsonObject, provider.response.content_path)
            ?: throw IOException("Could not extract content from response using path: ${provider.response.content_path}")

        return AIResponse(
            content = content,
            tokensUsed = extractTokensUsed(jsonObject)
        )
    }

    /**
     * Extract content from JSON using path notation
     * Supports simple paths like "choices[0].message.content"
     */
    private fun extractContent(jsonObject: JsonObject, path: String): String? {
        val parts = path.split(".")
        var current: Any? = jsonObject

        for (part in parts) {
            if (part.contains("[")) {
                // Handle array access like "choices[0]"
                val arrayName = part.substringBefore("[")
                val index = part.substringAfter("[").substringBefore("]").toInt()
                
                current = (current as? JsonObject)?.get(arrayName)
                    ?.let { jsonElement ->
                        jsonElement.jsonObject.values.elementAtOrNull(index)
                    }
            } else {
                // Handle object property
                current = (current as? JsonObject)?.get(part)
            }

            if (current == null) return null
        }

        return (current as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

    /**
     * Extract tokens used from response (if available)
     */
    private fun extractTokensUsed(jsonObject: JsonObject): Int? {
        return jsonObject["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.content?.toIntOrNull()
            ?: jsonObject["usage"]?.jsonObject?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull()
    }
}
