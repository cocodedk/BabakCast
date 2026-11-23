package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.AIResponse
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** A provider check ends in one plain outcome the settings dialog can say in words. */
class ProviderCheckOutcomeTest {
    private fun classify(result: Result<AIResponse>) = ProviderCheckOutcome.classify(result, elapsedMs = 1200)

    private fun http(code: Int, error: AppError = AppError.AiRequestFailed()) =
        Result.failure<AIResponse>(AppErrorException(error, "API request failed: $code", AiHttpStatus(code)))

    @Test
    fun success_works() {
        assertEquals(ProviderCheckOutcome.Works(1200), classify(Result.success(AIResponse("OK"))))
    }

    @Test
    fun unreadableReply_isUnreadable() {
        val e = AppErrorException(AppError.AiResponseUnreadable(), "no text part")
        assertEquals(ProviderCheckOutcome.Unreadable, classify(Result.failure(e)))
    }

    @Test
    fun unauthorizedOrForbidden_isKeyRejected() {
        assertEquals(ProviderCheckOutcome.KeyRejected, classify(http(401)))
        assertEquals(ProviderCheckOutcome.KeyRejected, classify(http(403)))
    }

    @Test
    fun notFound_isNotFound() {
        assertEquals(ProviderCheckOutcome.NotFound, classify(http(404)))
    }

    @Test
    fun tooManyRequestsOrQuota_isUsageLimit() {
        assertEquals(ProviderCheckOutcome.UsageLimit, classify(http(429, AppError.ApiQuotaExceeded())))
        assertEquals(ProviderCheckOutcome.UsageLimit, classify(http(400, AppError.ApiQuotaExceeded())))
    }

    @Test
    fun otherHttpCodes_areRefusedWithTheCode() {
        assertEquals(ProviderCheckOutcome.Refused(400), classify(http(400)))
        assertEquals(ProviderCheckOutcome.Refused(500), classify(http(500)))
    }

    @Test
    fun networkAndBadEndpoint_areUnreachable() {
        listOf(
            UnknownHostException("api.example"),
            SocketTimeoutException("timeout"),
            ConnectException("refused"),
            IllegalArgumentException("Expected URL scheme 'http' or 'https'")
        ).forEach { assertEquals(it.toString(), ProviderCheckOutcome.Unreachable, classify(Result.failure(it))) }
    }
}
