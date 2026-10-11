package com.cocode.babakcast.domain.video

import androidx.annotation.StringRes
import com.cocode.babakcast.R

/**
 * The trim controls exactly as the user left them. [enabled] off is the default,
 * and every download then runs the untrimmed path unchanged.
 */
data class TrimInput(
    val enabled: Boolean = false,
    val start: TrimTimeFields = TrimTimeFields(),
    val end: TrimTimeFields = TrimTimeFields()
) {
    /**
     * Single source of truth for whether these fields describe a usable cut —
     * the UI calls it to show the validation message and disable the download
     * buttons, the ViewModel calls it to decide whether to trim at all.
     */
    fun resolve(): TrimResolution {
        if (!enabled) return TrimResolution.Disabled

        val startMs = start.toMillis() ?: return TrimResolution.Invalid(
            problemWith(start, R.string.trim_error_start_missing, R.string.trim_error_start_invalid)
        )
        val endMs = end.toMillis() ?: return TrimResolution.Invalid(
            problemWith(end, R.string.trim_error_end_missing, R.string.trim_error_end_invalid)
        )
        if (endMs <= startMs) {
            return TrimResolution.Invalid(R.string.trim_error_order)
        }
        if (endMs - startMs < TrimRange.MIN_DURATION_MS) {
            return TrimResolution.Invalid(R.string.trim_error_too_short)
        }
        return TrimResolution.Ready(TrimRange(startMs, endMs))
    }

    /** Tells apart boxes nobody has filled in yet from boxes holding something unusable. */
    @StringRes
    private fun problemWith(
        fields: TrimTimeFields,
        @StringRes missing: Int,
        @StringRes invalid: Int
    ): Int = if (fields.isBlank()) missing else invalid
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
