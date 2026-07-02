package com.cocode.babakcast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryTest {

    @Test
    fun returnsResultOnFirstSuccess() {
        var calls = 0
        val result = retry(maxAttempts = 3) {
            calls++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun succeedsAfterTransientFailures() {
        var calls = 0
        var retries = 0
        val result = retry(
            maxAttempts = 3,
            onRetry = { _, _ -> retries++ }
        ) {
            calls++
            if (calls < 3) throw RuntimeException("HTTP Error 403: Forbidden")
            "downloaded"
        }
        assertEquals("downloaded", result)
        assertEquals(3, calls)
        assertEquals(2, retries)
    }

    @Test
    fun throwsLastErrorAfterAllAttemptsFail() {
        var calls = 0
        var message: String? = null
        try {
            retry(maxAttempts = 3) {
                calls++
                throw IllegalStateException("fail #$calls")
            }
        } catch (e: Exception) {
            message = e.message
        }
        assertEquals(3, calls)
        assertEquals("fail #3", message)
    }

    @Test
    fun rejectsNonPositiveMaxAttempts() {
        var caught: Exception? = null
        try {
            retry(maxAttempts = 0) { "never" }
        } catch (e: IllegalArgumentException) {
            caught = e
        }
        assertTrue(caught is IllegalArgumentException)
    }
}
