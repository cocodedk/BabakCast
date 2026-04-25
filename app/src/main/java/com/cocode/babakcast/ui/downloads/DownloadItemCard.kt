package com.cocode.babakcast.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocode.babakcast.ui.theme.BabakCastColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DownloadItemCard(
    item: DownloadItem,
    shape: Shape,
    onPlay: () -> Unit,
    onShareDownload: () -> Unit,
    onShareTitle: () -> Unit,
    onDelete: () -> Unit
) {
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
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Play download",
                    tint = BabakCastColors.PrimaryAccent
                )
            }
            IconButton(onClick = onShareDownload) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share download",
                    tint = BabakCastColors.SecondaryAccent
                )
            }
            IconButton(onClick = onShareTitle) {
                Icon(
                    imageVector = Icons.Outlined.Create,
                    contentDescription = "Share title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete download",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = com.cocode.babakcast.util.ByteFormatter.format(bytes)

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
