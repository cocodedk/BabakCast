package com.cocode.babakcast.util

/** Builds the human-facing caption shared alongside extracted audio. */
object AudioShareCaption {
    fun build(title: String, partCount: Int): String {
        val base = title.ifBlank { "Audio" }
        return if (partCount > 1) "$base — $partCount parts, play in order" else base
    }
}
