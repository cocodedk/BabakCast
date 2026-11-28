package com.cocode.babakcast.data.remote

/** HTTP status codes the AI client and the provider check act on. */
internal object HttpStatus {
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val TOO_MANY_REQUESTS = 429
}
