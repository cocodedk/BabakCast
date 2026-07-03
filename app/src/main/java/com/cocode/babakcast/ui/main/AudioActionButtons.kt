package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.ui.theme.BabakCastColors

/**
 * The two audio actions: keep the extracted MP3 whole (primary) or split it for
 * sharing limits (secondary). Download progress is shown by the shared progress
 * indicator below the button column, matching the video buttons.
 */
@Composable
internal fun AudioActionButtons(
    uiState: MainUiState,
    onDownloadAudio: () -> Unit,
    onDownloadSplitAudio: () -> Unit
) {
    val audioEnabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AudioButton(
            label = "Download Audio",
            enabled = audioEnabled,
            color = BabakCastColors.PrimaryAccent,
            onClick = onDownloadAudio
        )
        AudioButton(
            label = "Download Audio Split (${uiState.splitSizeMb} MB)",
            enabled = audioEnabled,
            color = MaterialTheme.colorScheme.onSurface,
            onClick = onDownloadSplitAudio
        )
    }
}

@Composable
private fun AudioButton(
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = color.copy(alpha = 0.3f)
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled).copy(
            brush = SolidColor(
                if (enabled) color.copy(alpha = 0.5f) else color.copy(alpha = 0.2f)
            )
        )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        )
    }
}
