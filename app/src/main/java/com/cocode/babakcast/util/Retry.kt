package com.cocode.babakcast.util

/**
 * Runs [block], retrying when it throws, up to [maxAttempts] total attempts. Returns
 * the first successful result; if every attempt fails, the last exception is rethrown.
 * [onRetry] is invoked with the 1-based number of the attempt that just failed and its
 * error, before each retry (e.g. to log). [maxAttempts] must be >= 1.
 *
 * Used to absorb YouTube's intermittent HTTP 403 on media downloads: re-running the
 * download re-extracts fresh signed URLs, which usually succeeds on the next attempt.
 */
fun <T> retry(
    maxAttempts: Int,
    onRetry: (attempt: Int, error: Exception) -> Unit = { _, _ -> },
    block: () -> T
): T {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    var lastError: Exception? = null
    for (attempt in 1..maxAttempts) {
        try {
            return block()
        } catch (e: Exception) {
            lastError = e
            if (attempt < maxAttempts) onRetry(attempt, e)
        }
    }
    throw lastError ?: IllegalStateException("retry: no attempts were made")
}
