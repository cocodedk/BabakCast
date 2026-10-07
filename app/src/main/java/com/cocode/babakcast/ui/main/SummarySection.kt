package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.ui.theme.BabakCastColors
import androidx.compose.ui.res.stringResource
import com.cocode.babakcast.R

@Composable
internal fun SummarySection(
    summary: String,
    shareChunkCount: Int,
    shareChunkIndex: Int,
    shareEnabled: Boolean,
    onCopySummary: () -> Unit,
    onShareSummaryAsFile: () -> Unit,
    onShareSummary: () -> Unit
) {
    val shareLabel = if (shareChunkCount > 1) {
        stringResource(R.string.summary_send_part, shareChunkIndex + 1, shareChunkCount)
    } else {
        stringResource(R.string.summary_share)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.summary_title),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onCopySummary,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        stringResource(R.string.action_copy),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                TextButton(
                    onClick = onShareSummaryAsFile,
                    enabled = shareEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        stringResource(R.string.summary_share_as_file),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                TextButton(
                    onClick = onShareSummary,
                    enabled = shareEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = BabakCastColors.SecondaryAccent,
                        disabledContentColor = BabakCastColors.SecondaryAccent.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        shareLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
