package com.cocode.babakcast.domain.video

/**
 * A half-open cut of a media file: [startMs] inclusive, [endMs] exclusive.
 *
 * Milliseconds rather than the tenths the input fields accept, because Media3's
 * ClippingConfiguration is millisecond-based — a tenth of a second is
 * representable exactly, so nothing is rounded at the cut boundary.
 */
data class TrimRange(val startMs: Long, val endMs: Long) {

    init {
        require(startMs >= 0L) { "startMs must be >= 0, got $startMs" }
        require(endMs - startMs >= MIN_DURATION_MS) {
            "segment must be at least $MIN_DURATION_MS ms, got ${endMs - startMs}"
        }
    }

    val durationMs: Long get() = endMs - startMs

    companion object {
        /** One tenth of a second — the finest cut the input format can express. */
        const val MIN_DURATION_MS = 100L
    }
}
