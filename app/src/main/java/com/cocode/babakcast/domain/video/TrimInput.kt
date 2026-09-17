package com.cocode.babakcast.domain.video

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
            ?: return TrimResolution.Invalid("Start time is not a valid timestamp — try 1:23.4")
        val endMs = TrimTimeParser.parseToMillis(end)
            ?: return TrimResolution.Invalid("End time is not a valid timestamp — try 1:23.4")
        if (endMs <= startMs) {
            return TrimResolution.Invalid("End time must come after the start time")
        }
        if (endMs - startMs < TrimRange.MIN_DURATION_MS) {
            return TrimResolution.Invalid("The segment must be at least a tenth of a second long")
        }
        return TrimResolution.Ready(TrimRange(startMs, endMs))
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
