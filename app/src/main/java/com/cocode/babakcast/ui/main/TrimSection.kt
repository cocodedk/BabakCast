package com.cocode.babakcast.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.domain.video.TrimInput
import com.cocode.babakcast.domain.video.TrimResolution
import com.cocode.babakcast.ui.theme.BabakCastColors

@Composable
internal fun TrimSection(
    trim: TrimInput,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrimTimeField(
                        label = "START",
                        value = trim.start,
                        enabled = enabled,
                        onValueChange = onStartChange,
                        modifier = Modifier.weight(1f)
                    )
                    TrimTimeField(
                        label = "END",
                        value = trim.end,
                        enabled = enabled,
                        onValueChange = onEndChange,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = (resolution as? TrimResolution.Invalid)?.message
                        ?: "Format m:ss.s — e.g. 1:23.4 (tenths of a second)",
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

@Composable
private fun TrimTimeField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            )
        },
        placeholder = {
            Text(
                "0:00.0",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = modifier,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BabakCastColors.PrimaryAccent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = BabakCastColors.PrimaryAccent,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        shape = MaterialTheme.shapes.medium
    )
}
