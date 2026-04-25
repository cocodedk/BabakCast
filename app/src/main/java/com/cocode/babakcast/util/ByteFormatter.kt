package com.cocode.babakcast.util

import java.util.Locale

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
            size.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", size)
        }
        return "$formatted ${UNITS[unitIndex]}"
    }
}
