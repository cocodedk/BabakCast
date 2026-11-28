package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.remote.AiKeyFormatError
import com.cocode.babakcast.util.AppErrorException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model list refuses a key with an invisible character before any request, and never echoes it. */
class AiModelFetcherKeyTest {
    private val fetcher = AiModelFetcher(OkHttpClient(), Json)

    @Test
    fun keyWithAnInvisibleCharacter_failsWithoutTheKeyInTheMessage() = runBlocking {
        for (provider in listOf("openai", "anthropic", "google-gemini", "openrouter")) {
            val failure = fetcher.fetchModelsForProvider(provider, "sk-abc\u200Bdef").exceptionOrNull()
            assertTrue(provider, failure is AppErrorException && failure.cause is AiKeyFormatError)
            assertFalse(provider, failure?.message.orEmpty().contains("sk-abc"))
        }
    }
}
