package com.cocode.babakcast.domain.split

import kotlin.math.min

/**
 * How much shorter the next attempt at an oversized part should be.
 *
 * A fixed 15% step cannot catch up with bursty or re-encoded media, where a
 * part can come out at nearly twice the cap and five attempts run out first.
 * Scaling by the overshoot, with 10% headroom, converges in one or two tries.
 */
object SplitRetry {
    private const val MIN_SHRINK = 0.85
    private const val HEADROOM = 0.9

    fun nextDuration(current: Double, producedBytes: Long, chunkBytes: Long): Double =
        current * min(MIN_SHRINK, chunkBytes.toDouble() / producedBytes * HEADROOM)
}
