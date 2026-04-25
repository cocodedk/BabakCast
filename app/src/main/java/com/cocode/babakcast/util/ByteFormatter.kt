package com.cocode.babakcast.util

import java.util.Locale
import kotlin.math.roundToLong

object ByteFormatter {
    private val UNITS = arrayOf("B", "KB", "MB", "GB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < UNITS.lastIndex) {
            size /= 1024
            unitIndex++
        }
        val formatted = if (size >= 100 || unitIndex == 0) {
            size.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.1f", size)
        }
        return "$formatted ${UNITS[unitIndex]}"
    }
}
