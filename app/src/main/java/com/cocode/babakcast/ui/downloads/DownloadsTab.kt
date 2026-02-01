package com.cocode.babakcast.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cocode.babakcast.ui.theme.BabakCastColors
import com.cocode.babakcast.ui.player.PlaybackItem
import com.cocode.babakcast.ui.player.VideoPlayerDialog
import com.cocode.babakcast.util.DownloadFileParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun DownloadsTab(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var activePlaybackItems by remember { mutableStateOf<List<PlaybackItem>>(emptyList()) }
    var activePlaybackStartIndex by remember { mutableStateOf(0) }
    var pendingPartSelection by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingDeleteSelection by remember { mutableStateOf<DownloadItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = viewModel::clearDownloads,
            enabled = !uiState.isCleaningDownloads,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = !uiState.isCleaningDownloads).copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        ) {
            if (uiState.isCleaningDownloads) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = BabakCastColors.PrimaryAccent
                )
            }
            Text(
                if (uiState.isCleaningDownloads) "Clearing Downloads…" else "Clear Downloads",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        uiState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            uiState.isLoadingDownloads -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = BabakCastColors.PrimaryAccent
                    )
                    Text(
                        text = "Loading downloads…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            uiState.downloadsError != null -> {
                Text(
                    text = "Failed to load downloads: ${uiState.downloadsError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.downloads.isEmpty() -> {
                Text(
                    text = "No downloads yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                uiState.downloads.forEachIndexed { index, item ->
                    val shape = when {
                        uiState.downloads.size == 1 -> MaterialTheme.shapes.medium
                        index == 0 -> MaterialTheme.shapes.medium.copy(
                            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                        )
                        index == uiState.downloads.lastIndex -> MaterialTheme.shapes.medium.copy(
                            topStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                            topEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                        )
                        else -> MaterialTheme.shapes.medium.copy(
                            topStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                            topEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
                            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val partsLabel = if (item.partCount > 1) {
                                    " • ${item.partCount} parts"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "${formatFileSize(item.sizeBytes)} • ${formatDate(item.lastModified)} • ${item.mediaType.label}$partsLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                if (item.files.size <= 1) {
                                    val file = item.files.firstOrNull()
                                    if (file != null) {
                                        openPlayback(
                                            file = file,
                                            downloads = uiState.downloads
                                        ) { items, startIndex ->
                                            activePlaybackItems = items
                                            activePlaybackStartIndex = startIndex
                                        }
                                    }
                                } else {
                                    pendingPartSelection = item
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = "Play download",
                                    tint = BabakCastColors.PrimaryAccent
                                )
                            }
                            IconButton(onClick = { viewModel.shareDownload(item) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share download",
                                    tint = BabakCastColors.SecondaryAccent
                                )
                            }
                            IconButton(onClick = { pendingDeleteSelection = item }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete download",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (pendingPartSelection != null) {
        val item = pendingPartSelection!!
        AlertDialog(
            onDismissRequest = { pendingPartSelection = null },
            title = { Text(text = "Choose part to play") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.files.forEachIndexed { index, file ->
                        TextButton(
                            onClick = {
                                openPlayback(
                                    file = file,
                                    downloads = uiState.downloads
                                ) { items, startIndex ->
                                    activePlaybackItems = items
                                    activePlaybackStartIndex = startIndex
                                }
                                pendingPartSelection = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = partLabel(file, index))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingPartSelection = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingDeleteSelection != null) {
        val item = pendingDeleteSelection!!
        AlertDialog(
            onDismissRequest = { pendingDeleteSelection = null },
            title = { Text(text = "Delete download?") },
            text = {
                Text(
                    text = "This will remove the downloaded file${if (item.partCount > 1) "s" else ""} from your device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownload(item)
                        pendingDeleteSelection = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSelection = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (activePlaybackItems.isNotEmpty()) {
        VideoPlayerDialog(
            items = activePlaybackItems,
            startIndex = activePlaybackStartIndex,
            autoPlayEnabled = uiState.autoPlayNext,
            onDismiss = {
                activePlaybackItems = emptyList()
                activePlaybackStartIndex = 0
            }
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    val formatted = if (size >= 100 || unitIndex == 0) {
        size.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", size)
    }
    return "$formatted ${units[unitIndex]}"
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun partLabel(file: File, index: Int): String {
    val base = file.nameWithoutExtension
    val number = DownloadFileParser.extractPartNumber(base)?.toString() ?: (index + 1).toString()
    return "Part $number"
}

private fun openPlayback(
    file: File,
    downloads: List<DownloadItem>,
    onReady: (List<PlaybackItem>, Int) -> Unit
) {
    val items = buildPlaybackItems(downloads)
    val startIndex = items.indexOfFirst { it.file.absolutePath == file.absolutePath }
    onReady(items, if (startIndex >= 0) startIndex else 0)
}

private fun buildPlaybackItems(downloads: List<DownloadItem>): List<PlaybackItem> {
    val items = mutableListOf<PlaybackItem>()
    downloads.forEach { item ->
        item.files.forEachIndexed { index, file ->
            val suffix = if (item.files.size > 1) " • ${partLabel(file, index)}" else ""
            items.add(
                PlaybackItem(
                    file = file,
                    title = item.displayName + suffix
                )
            )
        }
    }
    return items
}
