package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.AIResponse
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the body of a successful AI provider reply.
 *
 * Every way the body can be unusable (invalid JSON, a root that is not an object, a missing
 * or wrongly shaped content path) ends as an [AppErrorException] with
 * [AppError.AiResponseUnreadable], so the screen shows the plain message and the
 * technical reason stays in the exception text for the log.
 */
internal object AiResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

    fun parse(responseBody: String, contentPath: String): AIResponse =
        try {
            parseUnchecked(responseBody, contentPath)
        } catch (e: AppErrorException) {
            throw e
        } catch (e: Exception) {
            throw unreadable("Could not read the response: ${e.message}", e)
        }

    private fun parseUnchecked(responseBody: String, contentPath: String): AIResponse {
        val element = json.parseToJsonElement(responseBody)
        if (element !is JsonObject) {
            val kind = if (element is JsonArray) "array" else "primitive"
            throw unreadable("Unexpected JSON root type: $kind")
        }

        val content = extractContent(element, contentPath)
            ?: throw unreadable("Could not extract content from response using path: $contentPath")

        return AIResponse(content = content, tokensUsed = extractTokensUsed(element))
    }

    /**
     * Extract content from JSON using path notation
     * Supports simple paths like "choices[0].message.content"
     */
    private fun extractContent(jsonObject: JsonObject, path: String): String? {
        val parts = path.split(".")
        var current: JsonElement = jsonObject

        for (part in parts) {
            val hasIndex = part.contains("[")
            val name = if (hasIndex) part.substringBefore("[") else part
            val index = if (hasIndex) part.substringAfter("[").substringBefore("]").toIntOrNull() else null

            if (name.isNotEmpty()) {
                val obj = current as? JsonObject ?: return null
                current = obj[name] ?: return null
            }

            if (index != null) {
                val array = current as? JsonArray ?: return null
                current = array.getOrNull(index) ?: return null
            }
        }

        // Only a JSON string is reply text; null, a number or a boolean would read as "null", "42" or "true".
        return (current as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    /** Tokens used, when the reply says so. */
    private fun extractTokensUsed(jsonObject: JsonObject): Int? {
        return jsonObject["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.content?.toIntOrNull()
            ?: jsonObject["usage"]?.jsonObject?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull()
    }

    private fun unreadable(technical: String, cause: Throwable? = null) =
        AppErrorException(AppError.AiResponseUnreadable(), technical, cause)
}
