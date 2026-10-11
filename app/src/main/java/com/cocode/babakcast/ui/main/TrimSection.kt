package com.cocode.babakcast.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocode.babakcast.domain.video.TrimInput
import com.cocode.babakcast.domain.video.TrimResolution
import com.cocode.babakcast.domain.video.TrimTimeFields
import com.cocode.babakcast.ui.theme.BabakCastColors

@Composable
internal fun TrimSection(
    trim: TrimInput,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onStartChange: (TrimTimeFields) -> Unit,
    onEndChange: (TrimTimeFields) -> Unit
) {
    val resolution = trim.resolve()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cut a segment",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    if (trim.enabled) {
                        "Only this segment is kept — the rest is discarded"
                    } else {
                        "Keeps the whole video"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BabakCastColors.PrimaryAccent.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = trim.enabled,
                enabled = enabled,
                onCheckedChange = onToggle
            )
        }

        AnimatedVisibility(visible = trim.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TrimTimeRow(
                    label = "STARTS AT",
                    fields = trim.start,
                    enabled = enabled,
                    onChange = onStartChange
                )
                TrimTimeRow(
                    label = "ENDS AT",
                    fields = trim.end,
                    enabled = enabled,
                    onChange = onEndChange
                )
                Text(
                    text = (resolution as? TrimResolution.Invalid)?.message ?: DEFAULT_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (resolution is TrimResolution.Invalid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private const val DEFAULT_HINT =
    "Counted from the beginning of the video. An empty box means zero — so for the " +
        "ten seconds starting at 1 minute 23.4 seconds, put 1 and 23.4 above, then 1 and 33.4."
