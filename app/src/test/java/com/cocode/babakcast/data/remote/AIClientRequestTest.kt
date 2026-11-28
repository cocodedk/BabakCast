package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.repository.PredefinedProviders
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException
import com.cocode.babakcast.util.ErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The HTTP request AIClient sends, and the failure it returns for a refused one. */
class AIClientRequestTest {
    private fun provider(id: String): Provider = PredefinedProviders.ALL.first { it.id == id }

    @Test
    fun anthropicRequest_carriesKeyAndVersionHeaders() {
        val request = AIClient.buildHttpRequest(provider("anthropic"), "sk-ant-test", "{}")
        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("sk-ant-test", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
    }

    @Test
    fun geminiRequest_putsTheModelInTheUrl() {
        val request = AIClient.buildHttpRequest(provider("google-gemini").copy(model = "m1"), "g-key", "{}")
        assertTrue(request.url.toString().endsWith("/models/m1:generateContent"))
        assertEquals("g-key", request.header("x-goog-api-key"))
    }

    @Test
    fun keyWithAnInvisibleCharacter_isRejectedWithoutTheKeyInTheMessage() {
        try {
            AIClient.buildHttpRequest(provider("anthropic"), "sk-abc\u200Bdef", "{}")
            fail("expected the key to be rejected")
        } catch (e: AppErrorException) {
            assertFalse(e.message.orEmpty().contains("sk-abc"))
            assertEquals(ProviderCheckOutcome.KeyRejected, ProviderCheckOutcome.classify(Result.failure(e), 0))
            assertTrue(ErrorHandler.handleException(e) is AppError.ProviderMisconfigured)
        }
    }

    @Test
    fun httpFailure_keepsTheCodeAndBody() {
        val failure = AIClient.httpFailure(400, "{\"error\":\"API_KEY_INVALID\"}")
        val status = failure.cause as AiHttpStatus
        assertEquals(400, status.code)
        assertTrue(status.body.contains("API_KEY_INVALID"))
        assertTrue(failure.error is AppError.AiRequestFailed)
        assertTrue(AIClient.httpFailure(429, "").error is AppError.ApiQuotaExceeded)
    }
}
