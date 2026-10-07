package com.cocode.babakcast.data.remote

import android.util.Log
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
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException

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
    private val tag = "AIClient"
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
        maxTokens: Int,
        readTimeoutMs: Long? = null
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
            Log.d(tag, "Requesting provider=${provider.id} url=$url")
            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader(provider.auth.header, "${provider.auth.prefix}$apiKey")
                .build()
            Log.d(
                tag,
                "HTTP request provider=${provider.id} url=$url headers=${headerNames(request.headers)} bodyLength=${requestBody.length}"
            )

            // Execute request
            // Derived client shares the pool/dispatcher; only the read timeout differs.
            val client = if (readTimeoutMs == null) okHttpClient
            else okHttpClient.newBuilder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(
                    tag,
                    "API error provider=${provider.id} code=${response.code} message=${response.message} " +
                        "contentType=${response.header("Content-Type")} headers=${response.headers} body=${errorBody.take(500)}"
                )
                return@withContext Result.failure(
                    AppErrorException(
                        error = requestError(response.code, errorBody),
                        message = "API request failed: ${response.code} - $errorBody"
                    )
                )
            }

            // Parse response
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(tag, "Empty response body provider=${provider.id} code=${response.code} headers=${response.headers}")
                return@withContext Result.failure(unreadable("Empty response body"))
            }
            if (responseBody.isBlank()) {
                Log.e(
                    tag,
                    "Blank response body provider=${provider.id} code=${response.code} contentType=${response.header("Content-Type")} headers=${response.headers}"
                )
                return@withContext Result.failure(unreadable("Empty response body"))
            }

            Log.d(
                tag,
                "Response provider=${provider.id} code=${response.code} contentType=${response.header("Content-Type")} length=${responseBody.length}"
            )

            val aiResponse = try {
                AiResponseParser.parse(responseBody, provider.response.content_path)
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Parse failed provider=${provider.id} path=${provider.response.content_path} body=${responseBody.take(500)}",
                    e
                )
                throw e
            }
            Result.success(aiResponse)
        } catch (e: Exception) {
            Log.e(tag, "Request failed provider=${provider.id}", e)
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

        val body = json.encodeToString(JsonObject.serializer(), jsonObject)
        Log.d(tag, "Request body provider=${provider.id} length=${body.length} snippet=${body.take(500)}")
        return body
    }

    /** A refused request: a usage limit has its own message, anything else is a general AI request failure. */
    private fun requestError(code: Int, body: String): AppError =
        if (code == 429 || body.contains("quota", ignoreCase = true)) {
            AppError.ApiQuotaExceeded()
        } else {
            AppError.AiRequestFailed()
        }

    /** A response BabakCast can't use. [technical] is for the log; the screen shows a plain message. */
    private fun unreadable(technical: String) = AppErrorException(AppError.AiResponseUnreadable(), technical)

    companion object {
        /**
         * Header names only. The auth header's value is the API key, and OkHttp redacts only
         * `Authorization`, not `api-key`, `x-api-key` or `x-goog-api-key`, so never log the values.
         */
        internal fun headerNames(headers: Headers): String = headers.names().sorted().joinToString()
    }
}
