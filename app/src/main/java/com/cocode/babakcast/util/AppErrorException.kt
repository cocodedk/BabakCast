package com.cocode.babakcast.util

import java.io.IOException

/**
 * A failure that already knows what the customer should read. [message] stays technical: it goes
 * to the log and to tests. The screen shows [error] instead of that text.
 */
class AppErrorException(
    val error: AppError,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
