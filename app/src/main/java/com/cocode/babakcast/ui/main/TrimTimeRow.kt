package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.domain.video.TrimTimeFields
import com.cocode.babakcast.ui.theme.BabakCastColors

/**
 * One point in time as three number boxes — hours, minutes, seconds.
 *
 * Separate boxes because the number keypad these fields open has no colon key, so a
 * single `1:23.4` field could not be filled in at all.
 */
@Composable
internal fun TrimTimeRow(
    label: String,
    fields: TrimTimeFields,
    enabled: Boolean,
    onChange: (TrimTimeFields) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrimTimeBox(
                label = "hours",
                value = fields.hours,
                enabled = enabled,
                onValueChange = { onChange(fields.copy(hours = it)) },
                modifier = Modifier.weight(1f)
            )
            TrimTimeBox(
                label = "min",
                value = fields.minutes,
                enabled = enabled,
                onValueChange = { onChange(fields.copy(minutes = it)) },
                modifier = Modifier.weight(1f)
            )
            TrimTimeBox(
                label = "sec",
                value = fields.seconds,
                enabled = enabled,
                onValueChange = { onChange(fields.copy(seconds = it)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TrimTimeBox(
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
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
        },
        placeholder = {
            Text(
                "0",
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
