package com.cocode.babakcast.ui.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.cocode.babakcast.domain.split.SplitMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.cocode.babakcast.R

@Composable
internal fun SplitModeDialog(
    prompt: SplitChoicePrompt,
    splitSizeMb: Int,
    onChoice: (SplitMode) -> Unit,
    onDismiss: () -> Unit
) {
    val bodyRes = when (prompt.mediaType) {
        SplitChoiceMediaType.VIDEO -> R.plurals.split_dialog_body_video
        SplitChoiceMediaType.AUDIO -> R.plurals.split_dialog_body_audio
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.split_dialog_title)) },
        text = {
            Text(
                text = pluralStringResource(bodyRes, prompt.chapterCount, prompt.chapterCount, splitSizeMb)
            )
        },
        confirmButton = {
            TextButton(onClick = { onChoice(SplitMode.CHAPTERS) }) {
                Text(stringResource(R.string.split_by_chapters))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(SplitMode.BY_SIZE) }) {
                Text(stringResource(R.string.split_use_chunks, splitSizeMb))
            }
        }
    )
}
