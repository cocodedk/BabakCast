package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.AIResponse
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException

/** The HTTP status and body of a refused AI request, carried as the cause of its [AppErrorException]. */
class AiHttpStatus(val code: Int, val body: String = "") : Exception("HTTP $code")

/** An API key an HTTP header can't carry (an invisible or non-ASCII character pasted with it). */
class AiKeyFormatError : Exception("The API key has characters an HTTP header can't carry")

/** What a provider check found, in terms the settings dialog can say in plain words. */
sealed interface ProviderCheckOutcome {
    data class Works(val elapsedMs: Long) : ProviderCheckOutcome
    data object Unreadable : ProviderCheckOutcome
    data object KeyRejected : ProviderCheckOutcome
    data object NotFound : ProviderCheckOutcome
    data object UsageLimit : ProviderCheckOutcome
    data class Refused(val code: Int) : ProviderCheckOutcome
    data object Unreachable : ProviderCheckOutcome

    companion object {
        private const val GEMINI_INVALID_KEY = "API_KEY_INVALID"

        /**
         * Anything without an HTTP status and not an unreadable reply (no network, a timeout,
         * an unknown host, a malformed endpoint URL) means the provider could not be reached.
         */
        fun classify(result: Result<AIResponse>, elapsedMs: Long): ProviderCheckOutcome {
            val failure = result.exceptionOrNull() ?: return Works(elapsedMs)
            val error = (failure as? AppErrorException)?.error
            val status = failure.cause as? AiHttpStatus
            val code = status?.code
            return when {
                failure.cause is AiKeyFormatError -> KeyRejected
                error is AppError.AiResponseUnreadable -> Unreadable
                error is AppError.ApiQuotaExceeded || code == HttpStatus.TOO_MANY_REQUESTS -> UsageLimit
                code == HttpStatus.UNAUTHORIZED || code == HttpStatus.FORBIDDEN -> KeyRejected
                // Gemini answers a wrong key with 400 and this reason instead of 401.
                code == HttpStatus.BAD_REQUEST && status.body.contains(GEMINI_INVALID_KEY) -> KeyRejected
                code == HttpStatus.NOT_FOUND -> NotFound
                code != null -> Refused(code)
                else -> Unreachable
            }
        }
    }
}
