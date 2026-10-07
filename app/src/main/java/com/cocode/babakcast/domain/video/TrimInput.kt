package com.cocode.babakcast.domain.video

import com.cocode.babakcast.R
import androidx.annotation.StringRes

/**
 * The trim controls exactly as the user left them. [enabled] off is the default,
 * and every download then runs the untrimmed path unchanged.
 */
data class TrimInput(
    val enabled: Boolean = false,
    val start: String = "",
    val end: String = ""
) {
    /**
     * Single source of truth for whether these fields describe a usable cut —
     * the UI calls it to show the validation message and disable the download
     * buttons, the ViewModel calls it to decide whether to trim at all.
     */
    fun resolve(): TrimResolution {
        if (!enabled) return TrimResolution.Disabled

        val startMs = TrimTimeParser.parseToMillis(start)
            ?: return TrimResolution.Invalid(R.string.trim_error_start)
        val endMs = TrimTimeParser.parseToMillis(end)
            ?: return TrimResolution.Invalid(R.string.trim_error_end)
        if (endMs <= startMs) {
            return TrimResolution.Invalid(R.string.trim_error_order)
        }
        if (endMs - startMs < TrimRange.MIN_DURATION_MS) {
            return TrimResolution.Invalid(R.string.trim_error_too_short)
        }
        return TrimResolution.Ready(TrimRange(startMs, endMs))
    }
}

/** What [TrimInput.resolve] made of the current fields. */
sealed interface TrimResolution {

    /** The toggle is off — downloads keep the whole video. */
    data object Disabled : TrimResolution

    /** The toggle is on but the fields do not describe a cut yet. */
    data class Invalid(@StringRes val messageRes: Int) : TrimResolution

    /** The toggle is on and [range] is ready to cut. */
    data class Ready(val range: TrimRange) : TrimResolution
}
