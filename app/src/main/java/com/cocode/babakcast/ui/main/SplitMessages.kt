package com.cocode.babakcast.ui.main

import android.content.Context
import com.cocode.babakcast.R
import com.cocode.babakcast.domain.split.SplitMode

/** The progress line shown while a video or audio file is being split. */
internal fun SplitMode.splittingMessage(context: Context, media: SplitChoiceMediaType): String {
    val res = when (this) {
        SplitMode.NONE -> error("$media has no splitting message in NONE mode")
        SplitMode.BY_SIZE -> when (media) {
            SplitChoiceMediaType.VIDEO -> R.string.splitting_video
            SplitChoiceMediaType.AUDIO -> R.string.splitting_audio
        }
        SplitMode.CHAPTERS -> when (media) {
            SplitChoiceMediaType.VIDEO -> R.string.splitting_video_by_chapters
            SplitChoiceMediaType.AUDIO -> R.string.splitting_audio_by_chapters
        }
    }
    return context.getString(res)
}

/** The progress line for part [currentPart] of [totalParts] while a file is being split. */
internal fun SplitMode.splittingProgressMessage(
    context: Context,
    media: SplitChoiceMediaType,
    currentPart: Int,
    totalParts: Int
): String {
    val res = when (this) {
        SplitMode.NONE -> error("$media has no progress message in NONE mode")
        SplitMode.BY_SIZE -> when (media) {
            SplitChoiceMediaType.VIDEO -> R.string.splitting_video_part
            SplitChoiceMediaType.AUDIO -> R.string.splitting_audio_part
        }
        SplitMode.CHAPTERS -> when (media) {
            SplitChoiceMediaType.VIDEO -> R.string.splitting_video_chapter
            SplitChoiceMediaType.AUDIO -> R.string.splitting_audio_chapter
        }
    }
    return context.getString(res, currentPart, totalParts)
}
