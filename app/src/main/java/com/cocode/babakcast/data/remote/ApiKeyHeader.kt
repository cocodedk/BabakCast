package com.cocode.babakcast.data.remote

import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException

/**
 * An API key made ready for an HTTP header. OkHttp rejects a header value with a character
 * outside printable ASCII and puts the whole value (the key) in its message, which would reach
 * the log, so such a key is refused here first, with a message that leaves the key out.
 */
internal object ApiKeyHeader {
    private val HEADER_SAFE = '!'..'~'

    /** The trimmed key; throws [AppErrorException] caused by [AiKeyFormatError] when a character can't travel. */
    fun clean(key: String): String {
        val trimmed = key.trim()
        if (trimmed.any { it !in HEADER_SAFE }) {
            val reason = AiKeyFormatError()
            throw AppErrorException(AppError.ProviderMisconfigured(), reason.message.orEmpty(), reason)
        }
        return trimmed
    }
}
