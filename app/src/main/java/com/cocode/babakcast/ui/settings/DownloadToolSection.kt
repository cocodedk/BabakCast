package com.cocode.babakcast.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cocode.babakcast.BuildConfig
import com.cocode.babakcast.R
import com.cocode.babakcast.data.repository.YtDlpUpdateResult

/**
 * The fdroid flavour never updates yt-dlp on its own, so this is where the user does it:
 * one button, and a line that says how it went. The github flavour updates by itself and
 * shows nothing here.
 */
@Composable
internal fun DownloadToolSection(viewModel: DownloadToolViewModel = hiltViewModel()) {
    if (BuildConfig.YTDLP_SELF_UPDATE) return
    val state by viewModel.state.collectAsState()

    SectionHeader(title = stringResource(R.string.settings_downloader))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_downloader_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = viewModel::update,
            enabled = state != DownloadToolState.Running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_downloader_update))
        }
        val message = downloadToolMessage(state)
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun downloadToolMessage(state: DownloadToolState): String? = when (state) {
    DownloadToolState.Idle -> null
    DownloadToolState.Running -> stringResource(R.string.settings_downloader_working)
    is DownloadToolState.Finished -> when (state.result) {
        YtDlpUpdateResult.Updated -> stringResource(R.string.settings_downloader_done)
        YtDlpUpdateResult.AlreadyLatest -> stringResource(R.string.settings_downloader_latest)
        YtDlpUpdateResult.DownloadRunning -> stringResource(R.string.settings_downloader_busy)
        YtDlpUpdateResult.Failed -> stringResource(R.string.settings_downloader_failed)
    }
}
