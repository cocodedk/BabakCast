package com.cocode.babakcast.data.remote

import com.cocode.babakcast.data.model.AIResponse
import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException

/** The HTTP status of a refused AI request, carried as the cause of its [AppErrorException]. */
class AiHttpStatus(val code: Int) : Exception("HTTP $code")

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
        /**
         * Anything without an HTTP status and not an unreadable reply (no network, a timeout,
         * an unknown host, a malformed endpoint URL) means the provider could not be reached.
         */
        fun classify(result: Result<AIResponse>, elapsedMs: Long): ProviderCheckOutcome {
            val failure = result.exceptionOrNull() ?: return Works(elapsedMs)
            val error = (failure as? AppErrorException)?.error
            val code = (failure.cause as? AiHttpStatus)?.code
            return when {
                error is AppError.AiResponseUnreadable -> Unreadable
                error is AppError.ApiQuotaExceeded || code == 429 -> UsageLimit
                code == 401 || code == 403 -> KeyRejected
                code == 404 -> NotFound
                code != null -> Refused(code)
                else -> Unreachable
            }
        }
    }
}
