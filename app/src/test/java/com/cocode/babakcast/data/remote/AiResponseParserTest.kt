package com.cocode.babakcast.data.remote

import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A reply that arrives with HTTP 200 but cannot be used must show the typed, plain message. */
class AiResponseParserTest {
    private val path = "choices[0].message.content"

    private fun assertUnreadable(body: String, contentPath: String = path) {
        try {
            AiResponseParser.parse(body, contentPath)
            fail("Expected AppErrorException for: $body")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.AiResponseUnreadable)
        }
    }

    @Test
    fun validReply_isParsedWithTokens() {
        val body = """{"choices":[{"message":{"content":"Hello"}}],"usage":{"total_tokens":42}}"""
        val result = AiResponseParser.parse(body, path)
        assertEquals("Hello", result.content)
        assertEquals(42, result.tokensUsed)
    }

    @Test
    fun validReplyWithoutUsage_hasNoTokens() {
        val result = AiResponseParser.parse("""{"choices":[{"message":{"content":"Hi"}}]}""", path)
        assertNull(result.tokensUsed)
    }

    @Test
    fun invalidJson_isUnreadable() = assertUnreadable("<html>Bad gateway</html>")

    @Test
    fun truncatedJson_isUnreadable() = assertUnreadable("""{"choices":[{"message":""")

    @Test
    fun arrayRoot_isUnreadable() = assertUnreadable("[1, 2]")

    @Test
    fun primitiveRoot_isUnreadable() = assertUnreadable("\"text\"")

    @Test
    fun missingContentPath_isUnreadable() = assertUnreadable("""{"choices":[]}""")

    @Test
    fun contentThatIsNotText_isUnreadable() = assertUnreadable("""{"choices":[{"message":{"content":{"a":1}}}]}""")

    @Test
    fun usageThatIsNotAnObject_isUnreadable() =
        assertUnreadable("""{"choices":[{"message":{"content":"Hi"}}],"usage":5}""")

    @Test
    fun unreadableError_keepsTheTechnicalReasonOutOfTheScreenText() {
        try {
            AiResponseParser.parse("<html>", path)
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertNull(e.error.detail)
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun headerNames_neverIncludeValues() {
        val headers = Headers.Builder()
            .add("Accept", "application/json")
            .add("x-goog-api-key", "SECRET-KEY-123")
            .add("api-key", "SECRET-KEY-456")
            .build()
        val logged = AIClient.headerNames(headers)
        assertEquals("Accept, api-key, x-goog-api-key", logged)
        assertFalse(logged.contains("SECRET"))
    }
}
