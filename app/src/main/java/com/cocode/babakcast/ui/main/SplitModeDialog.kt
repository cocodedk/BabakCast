package com.cocode.babakcast.ui.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.cocode.babakcast.domain.split.SplitMode

@Composable
internal fun SplitModeDialog(
    prompt: SplitChoicePrompt,
    onChoice: (SplitMode) -> Unit,
    onDismiss: () -> Unit
) {
    val mediaLabel = when (prompt.mediaType) {
        SplitChoiceMediaType.VIDEO -> "video"
        SplitChoiceMediaType.AUDIO -> "audio"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Choose split mode") },
        text = {
            Text(
                text = "This $mediaLabel source includes ${prompt.chapterCount} chapters. " +
                    "Split by chapters or keep standard 16 MB chunks?"
            )
        },
        confirmButton = {
            TextButton(onClick = { onChoice(SplitMode.CHAPTERS) }) {
                Text("Split by chapters")
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(SplitMode.SIZE_16MB) }) {
                Text("Use 16 MB chunks")
            }
        }
    )
}
