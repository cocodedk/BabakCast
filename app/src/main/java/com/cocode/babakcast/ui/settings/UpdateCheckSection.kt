package com.cocode.babakcast.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.domain.network.NetworkType
import com.cocode.babakcast.domain.update.AppVersion
import com.cocode.babakcast.ui.theme.BabakCastColors
import com.cocode.babakcast.util.ByteFormatter

@Composable
internal fun UpdateCheckSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onConfirmCellular: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "UPDATES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (state) {
            UpdateUiState.Idle -> CheckButton(label = "Check for updates", onClick = onCheck)
            UpdateUiState.Checking -> CheckingRow()
            is UpdateUiState.UpToDate -> UpToDateCard(state, onDismiss = onDismiss, onCheckAgain = onCheck)
            is UpdateUiState.Available -> AvailableCard(
                state = state,
                onDownload = onDownload,
                onConfirmCellular = onConfirmCellular,
                onDismiss = onDismiss
            )
            is UpdateUiState.Failed -> FailedCard(state.reason, onRetry = onCheck, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun CheckButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BabakCastColors.PrimaryAccent)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun CheckingRow() {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = BabakCastColors.PrimaryAccent
            )
            Text("Checking for updates…", fontSize = 14.sp)
        }
    }
}

@Composable
private fun UpToDateCard(
    state: UpdateUiState.UpToDate,
    onDismiss: () -> Unit,
    onCheckAgain: () -> Unit
) {
    InfoCard(spacing = 6.dp) {
        Text("You're on the latest version", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("Installed: ${state.installed.display()}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text("OK") }
            TextButton(onClick = onCheckAgain) { Text("Check again") }
        }
    }
}

@Composable
private fun AvailableCard(
    state: UpdateUiState.Available,
    onDownload: () -> Unit,
    onConfirmCellular: () -> Unit,
    onDismiss: () -> Unit
) {
    InfoCard(spacing = 8.dp) {
        Text("Update available", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "New: ${state.latest.display()}    •    Installed: ${state.installed.display()}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Download size: ${ByteFormatter.format(state.apkSizeBytes)}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            state.networkType == NetworkType.OFFLINE -> {
                Text(
                    "You're offline. Connect to the internet and try again.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
            state.awaitingCellularConfirm -> {
                Spacer(Modifier.height(2.dp))
                Text(
                    "You're on cellular data. The download will use about ${ByteFormatter.format(state.apkSizeBytes)} of your plan.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Wait for Wi-Fi") }
                    Button(
                        onClick = onConfirmCellular,
                        colors = ButtonDefaults.buttonColors(containerColor = BabakCastColors.PrimaryAccent)
                    ) { Text("Download anyway") }
                }
            }
            else -> {
                val networkLabel = when (state.networkType) {
                    NetworkType.WIFI -> "You're on Wi-Fi."
                    NetworkType.CELLULAR -> "You're on cellular."
                    NetworkType.OTHER -> "You're connected."
                    NetworkType.OFFLINE -> ""
                }
                Text(networkLabel, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Later") }
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = BabakCastColors.PrimaryAccent)
                    ) { Text("Download now") }
                }
            }
        }
    }
}

@Composable
private fun FailedCard(reason: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    InfoCard(spacing = 6.dp) {
        Text("Couldn't check for updates", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun InfoCard(
    spacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) { content() }
}

private fun AppVersion.display() = "$major.$minor.$patch"
