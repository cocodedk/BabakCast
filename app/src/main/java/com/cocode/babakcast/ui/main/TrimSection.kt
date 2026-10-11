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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocode.babakcast.R
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
                    stringResource(R.string.trim_title),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    if (trim.enabled) {
                        stringResource(R.string.trim_hint_on)
                    } else {
                        stringResource(R.string.trim_hint_off)
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
                    label = stringResource(R.string.trim_start),
                    fields = trim.start,
                    enabled = enabled,
                    onChange = onStartChange
                )
                TrimTimeRow(
                    label = stringResource(R.string.trim_end),
                    fields = trim.end,
                    enabled = enabled,
                    onChange = onEndChange
                )
                Text(
                    text = (resolution as? TrimResolution.Invalid)
                        ?.let { stringResource(it.messageRes) }
                        ?: stringResource(R.string.trim_format_hint),
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
