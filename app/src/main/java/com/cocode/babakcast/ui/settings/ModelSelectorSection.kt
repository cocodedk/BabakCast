package com.cocode.babakcast.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.ui.theme.BabakCastColors

private const val FREE_MODEL_SUFFIX = ":free"
private fun String.isFreeModel() = endsWith(FREE_MODEL_SUFFIX)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSelectorSection(
    modelsToShow: List<String>,
    modelsLoading: Boolean,
    modelsError: String?,
    selectedModel: String,
    showModelDropdown: Boolean,
    onModelChange: (String) -> Unit,
    onToggleDropdown: () -> Unit,
    onDismissDropdown: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Model",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (modelsLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = BabakCastColors.PrimaryAccent
            )
        }
        if (modelsError != null) {
            Text(
                text = modelsError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        ExposedDropdownMenuBox(
            expanded = showModelDropdown,
            onExpandedChange = { onToggleDropdown() }
        ) {
            OutlinedTextField(
                value = selectedModel,
                onValueChange = onModelChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                readOnly = false,
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Select model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BabakCastColors.PrimaryAccent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    cursorColor = BabakCastColors.PrimaryAccent
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                shape = MaterialTheme.shapes.small,
                placeholder = {
                    Text(
                        "Select or enter model",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                    )
                }
            )

            if (modelsToShow.isNotEmpty()) {
                val orderedModels = remember(modelsToShow) {
                    modelsToShow.sortedByDescending(String::isFreeModel)
                }
                ExposedDropdownMenu(
                    expanded = showModelDropdown,
                    onDismissRequest = onDismissDropdown,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    orderedModels.forEach { model ->
                        DropdownMenuItem(
                            text = { ModelRow(model = model, isSelected = model == selectedModel) },
                            onClick = { onModelChange(model) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = "Select from list or type a custom model name",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ModelRow(model: String, isSelected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = model,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = if (isSelected) BabakCastColors.PrimaryAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (model.isFreeModel()) FreeBadge()
    }
}

@Composable
private fun FreeBadge() {
    Text(
        text = "FREE",
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        ),
        color = BabakCastColors.OnSuccess,
        modifier = Modifier
            .background(BabakCastColors.Success, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
