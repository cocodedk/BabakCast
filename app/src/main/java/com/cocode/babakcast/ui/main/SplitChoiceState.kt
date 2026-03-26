package com.cocode.babakcast.ui.main

data class SplitChoicePrompt(
    val mediaType: SplitChoiceMediaType,
    val chapterCount: Int
)

enum class SplitChoiceMediaType {
    VIDEO,
    AUDIO
}
