package com.cocode.babakcast.util

/**
 * Builds the combined original + Persian translation text for sharing.
 * The U+200F (right-to-left mark) makes bidi renderers (e.g. WhatsApp)
 * lay out the Persian block correctly after the LTR original.
 */
object TranslatedShareText {
    private const val SEPARATOR = "\n\n———\n\n"
    private const val RTL_MARK = "‏"

    fun combine(original: String, translated: String): String =
        original + SEPARATOR + RTL_MARK + translated
}
