package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.AIMessage
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.repository.PredefinedProviders
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each provider gets the request body its own API documents. */
class ChatRequestBodyTest {
    private fun provider(id: String): Provider = PredefinedProviders.ALL.first { it.id == id }

    private val messages = listOf(
        AIMessage(role = "system", content = "Be brief."),
        AIMessage(role = "user", content = "Say OK.")
    )

    private fun body(id: String, msgs: List<AIMessage> = messages): JsonObject =
        ChatRequestBody.build(provider(id), msgs, temperature = 0.2, maxTokens = 100)

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun openAiStyle_keepsTodayShape() {
        val b = body("openai")
        assertEquals("gpt-4o-mini", b.text("model"))
        val msgs = b.getValue("messages").jsonArray
        assertEquals(2, msgs.size)
        assertEquals("system", msgs[0].jsonObject.text("role"))
        assertEquals("Be brief.", msgs[0].jsonObject.text("content"))
        assertEquals("user", msgs[1].jsonObject.text("role"))
        assertEquals("Say OK.", msgs[1].jsonObject.text("content"))
        assertEquals(JsonPrimitive(0.2), b["temperature"])
        assertEquals(JsonPrimitive(100), b["max_tokens"])
    }

    @Test
    fun anthropic_movesSystemToTopLevel() {
        val b = body("anthropic")
        assertEquals("Be brief.", b.text("system"))
        val msgs = b.getValue("messages").jsonArray
        assertEquals(1, msgs.size)
        assertEquals("user", msgs[0].jsonObject.text("role"))
        assertEquals("Say OK.", msgs[0].jsonObject.text("content"))
        assertTrue(b.containsKey("model"))
        assertEquals(JsonPrimitive(100), b["max_tokens"])
        assertEquals(JsonPrimitive(0.2), b["temperature"])
    }

    @Test
    fun anthropic_sendsVersionHeader() {
        assertEquals(mapOf("anthropic-version" to "2023-06-01"), ChatRequestBody.extraHeaders(provider("anthropic")))
        listOf("openai", "openrouter", "azure-openai", "google-gemini").forEach {
            assertEquals(it, emptyMap<String, String>(), ChatRequestBody.extraHeaders(provider(it)))
        }
    }

    @Test
    fun gemini_usesPartsAndModelRole() {
        val withReply = messages + AIMessage(role = "assistant", content = "OK")
        val b = body("google-gemini", withReply)
        val contents = b.getValue("contents").jsonArray
        assertEquals(2, contents.size)
        assertEquals("user", contents[0].jsonObject.text("role"))
        assertEquals("Say OK.", contents[0].jsonObject.getValue("parts").jsonArray[0].jsonObject.text("text"))
        assertEquals("model", contents[1].jsonObject.text("role"))
        val system = b.getValue("systemInstruction").jsonObject.getValue("parts").jsonArray[0].jsonObject
        assertEquals("Be brief.", system.text("text"))
    }

    @Test
    fun gemini_nestsGenerationConfig() {
        val b = body("google-gemini")
        val config = b.getValue("generationConfig").jsonObject
        assertEquals(JsonPrimitive(0.2), config["temperature"])
        assertEquals(JsonPrimitive(100), config["maxOutputTokens"])
        assertFalse(b.containsKey("generationConfig.temperature"))
        assertFalse(b.containsKey("model"))
    }
}
