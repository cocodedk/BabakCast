package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.AIMessage
import com.cocode.babakcast.data.model.Provider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds a chat request in the format each provider documents. Anthropic wants the system
 * prompt at the top level and a version header; Gemini wants `parts`, the role `model` for
 * replies and the system prompt in `systemInstruction`. Every other provider gets the OpenAI
 * chat format. A dotted path such as `generationConfig.temperature` becomes nested objects.
 */
internal object ChatRequestBody {
    private const val ANTHROPIC = "anthropic"
    private const val GEMINI = "google-gemini"
    private const val SYSTEM = "system"

    fun build(provider: Provider, messages: List<AIMessage>, temperature: Double, maxTokens: Int): JsonObject {
        val request = provider.request
        val system = messages.filter { it.role == SYSTEM }.joinToString("\n\n") { it.content }.ifBlank { null }
        val fields = mutableMapOf<String, JsonElement>()
        when (provider.id) {
            ANTHROPIC -> {
                system?.let { fields[SYSTEM] = JsonPrimitive(it) }
                fields[request.messages_path] = chatMessages(messages.filterNot { it.role == SYSTEM })
            }
            GEMINI -> {
                system?.let { fields["systemInstruction"] = parts(it) }
                fields[request.messages_path] = JsonArray(messages.filterNot { it.role == SYSTEM }.map(::geminiContent))
            }
            else -> fields[request.messages_path] = chatMessages(messages)
        }
        putPath(fields, request.temperature_path, JsonPrimitive(temperature))
        putPath(fields, request.max_tokens_path, JsonPrimitive(maxTokens))
        // Gemini takes the model from the URL and rejects unknown fields.
        if (request.type == "chat" && provider.id != GEMINI) fields["model"] = JsonPrimitive(provider.model)
        return JsonObject(fields)
    }

    /** Headers the provider requires besides its auth header. */
    fun extraHeaders(provider: Provider): Map<String, String> =
        if (provider.id == ANTHROPIC) mapOf("anthropic-version" to "2023-06-01") else emptyMap()

    private fun chatMessages(messages: List<AIMessage>) = JsonArray(
        messages.map { JsonObject(mapOf("role" to JsonPrimitive(it.role), "content" to JsonPrimitive(it.content))) }
    )

    private fun geminiContent(message: AIMessage): JsonObject {
        val role = if (message.role == "assistant") "model" else "user"
        return JsonObject(mapOf("role" to JsonPrimitive(role)) + parts(message.content))
    }

    private fun parts(text: String) =
        JsonObject(mapOf("parts" to JsonArray(listOf(JsonObject(mapOf("text" to JsonPrimitive(text)))))))

    private fun putPath(fields: MutableMap<String, JsonElement>, path: String, value: JsonElement) {
        val head = path.substringBefore('.')
        if (head == path) {
            fields[path] = value
            return
        }
        val inner = (fields[head] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        putPath(inner, path.substringAfter('.'), value)
        fields[head] = JsonObject(inner)
    }
}
