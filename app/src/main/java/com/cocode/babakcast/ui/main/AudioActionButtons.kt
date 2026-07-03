package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.ui.theme.BabakCastColors

@Composable
internal fun AudioActionButtons(
    uiState: MainUiState,
    onDownloadAudio: () -> Unit,
    onDownloadSplitAudio: () -> Unit
) {
    val audioEnabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onDownloadAudio,
            enabled = audioEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = audioEnabled).copy(
                brush = SolidColor(
                    if (audioEnabled) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    }
                )
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isDownloadingAudio) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = BabakCastColors.PrimaryAccent
                    )
                }
                Text(
                    if (uiState.isDownloadingAudio) "Downloading Audio…" else "Download Audio",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                )
            }
        }

        OutlinedButton(
            onClick = onDownloadSplitAudio,
            enabled = audioEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BabakCastColors.PrimaryAccent,
                disabledContentColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.3f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = audioEnabled).copy(
                brush = SolidColor(
                    if (audioEnabled) {
                        BabakCastColors.PrimaryAccent.copy(alpha = 0.5f)
                    } else {
                        BabakCastColors.PrimaryAccent.copy(alpha = 0.2f)
                    }
                )
            )
        ) {
            Text(
                "Download Audio Split (${uiState.splitSizeMb} MB)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )
        }
    }
}
