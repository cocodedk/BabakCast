package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.cocode.babakcast.ui.theme.BabakCastColors
import androidx.compose.ui.res.stringResource
import com.cocode.babakcast.R

@Composable
internal fun TranslateToggleRow(
    uiState: MainUiState,
    onToggle: (Boolean) -> Unit,
    onShareNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.translate_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                if (uiState.isTranslatingForShare) stringResource(R.string.translate_in_progress)
                else stringResource(R.string.translate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = BabakCastColors.PrimaryAccent.copy(alpha = 0.7f)
            )
        }
        if (uiState.isTranslatingForShare) {
            TextButton(onClick = onShareNow) { Text(stringResource(R.string.translate_share_now)) }
        } else {
            Switch(
                checked = uiState.translateBeforeShare,
                onCheckedChange = onToggle
            )
        }
    }
}
