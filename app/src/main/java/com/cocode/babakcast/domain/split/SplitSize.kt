package com.cocode.babakcast.domain.split

object SplitSize {
    const val MIN_MB = 5
    const val MAX_MB = 100
    const val DEFAULT_MB = 16
    const val DEFAULT_BYTES = DEFAULT_MB * 1024L * 1024L
}
