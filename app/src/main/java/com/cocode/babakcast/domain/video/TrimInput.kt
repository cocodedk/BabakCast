package com.cocode.babakcast.domain.video

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

        val startMs = start.toMillis() ?: return TrimResolution.Invalid(problemWith("start", start))
        val endMs = end.toMillis() ?: return TrimResolution.Invalid(problemWith("end", end))
        if (endMs <= startMs) {
            return TrimResolution.Invalid("The end time has to be later than the start time.")
        }
        if (endMs - startMs < TrimRange.MIN_DURATION_MS) {
            return TrimResolution.Invalid("The clip has to be at least a tenth of a second long.")
        }
        return TrimResolution.Ready(TrimRange(startMs, endMs))
    }

    /** Says whether [fields] is simply unfilled or holds something unusable. */
    private fun problemWith(which: String, fields: TrimTimeFields): String =
        if (fields.isBlank()) {
            "Fill in the $which time. You can leave the hour box empty."
        } else {
            "Check the $which time: digits only, with minutes and seconds below 60."
        }
}

/** What [TrimInput.resolve] made of the current fields. */
sealed interface TrimResolution {

    /** The toggle is off — downloads keep the whole video. */
    data object Disabled : TrimResolution

    /** The toggle is on but the fields do not describe a cut yet. */
    data class Invalid(val message: String) : TrimResolution

    /** The toggle is on and [range] is ready to cut. */
    data class Ready(val range: TrimRange) : TrimResolution
}
