package com.cocode.babakcast.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.ui.theme.BabakCastColors

@Composable
internal fun GeneralSettingsSection(
    defaultLanguage: String,
    adaptiveSummaryLength: Boolean,
    defaultSummaryLength: SummaryLength,
    autoPlayNext: Boolean,
    onLanguageChange: (String) -> Unit,
    onAdaptiveLengthChange: (Boolean) -> Unit,
    onSummaryLengthChange: (SummaryLength) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit
) {
    SectionHeader(title = "Defaults")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SUMMARY / TRANSLATION LANGUAGE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = defaultLanguage,
                onValueChange = onLanguageChange,
                placeholder = {
                    Text(
                        "e.g. en, es, fa, German",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BabakCastColors.PrimaryAccent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = BabakCastColors.PrimaryAccent
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                shape = MaterialTheme.shapes.medium
            )
        }

        SummaryLengthRow(
            adaptiveEnabled = adaptiveSummaryLength,
            length = defaultSummaryLength,
            onAdaptiveChange = onAdaptiveLengthChange,
            onLengthChange = onSummaryLengthChange
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    SectionHeader(title = "Playback")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Auto-play next video",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Automatically play the next download after a video ends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoPlayNext,
                    onCheckedChange = onAutoPlayChange
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp, top = 4.dp)
    )
}

@Composable
internal fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = settingsRowShape(isFirst = isFirst, isLast = isLast),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SummaryLengthRow(
    adaptiveEnabled: Boolean,
    length: SummaryLength,
    onAdaptiveChange: (Boolean) -> Unit,
    onLengthChange: (SummaryLength) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Adaptive length",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically adjust summary length",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = adaptiveEnabled,
                    onCheckedChange = onAdaptiveChange
                )
            }
        }

        SettingsRow(
            label = "Summary length",
            value = if (adaptiveEnabled) {
                "Automatic"
            } else {
                length.name.lowercase().replaceFirstChar { it.uppercase() }
            },
            onClick = { if (!adaptiveEnabled) showDialog = true },
            isFirst = true,
            isLast = true
        )
    }

    if (showDialog && !adaptiveEnabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Summary length") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryLength.values().forEach { option ->
                        TextButton(
                            onClick = {
                                onLengthChange(option)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun settingsRowShape(isFirst: Boolean, isLast: Boolean) = when {
    isFirst && isLast -> MaterialTheme.shapes.medium
    isFirst -> MaterialTheme.shapes.medium.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp)
    )
    isLast -> MaterialTheme.shapes.medium.copy(
        topStart = CornerSize(0.dp),
        topEnd = CornerSize(0.dp)
    )
    else -> MaterialTheme.shapes.medium.copy(
        topStart = CornerSize(0.dp),
        topEnd = CornerSize(0.dp),
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp)
    )
}
