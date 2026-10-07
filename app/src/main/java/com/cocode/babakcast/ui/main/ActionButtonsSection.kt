package com.cocode.babakcast.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.data.model.SummaryLength
import com.cocode.babakcast.domain.split.SplitSize
import com.cocode.babakcast.ui.theme.BabakCastColors
import com.cocode.babakcast.util.urlparsing.XUrlExtractor
import androidx.compose.ui.res.stringResource
import com.cocode.babakcast.R
import com.cocode.babakcast.ui.labelRes

@Composable
internal fun ActionButtonsSection(
    uiState: MainUiState,
    onDownloadVideo: () -> Unit,
    onDownloadSplitVideo: () -> Unit,
    onSplitSizeChange: (Int) -> Unit,
    onDownloadAllMedia: () -> Unit,
    onCopyTweetText: () -> Unit,
    onShareTweetText: () -> Unit,
    onDownloadAudio: () -> Unit,
    onDownloadSplitAudio: () -> Unit,
    onSummarize: () -> Unit,
    onSummaryLengthChange: (SummaryLength) -> Unit,
    onTranslateToggle: (Boolean) -> Unit,
    onShareNow: () -> Unit,
    onTrimToggle: (Boolean) -> Unit,
    onTrimStartChange: (String) -> Unit,
    onTrimEndChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TranslateToggleRow(uiState, onTranslateToggle, onShareNow)

        val controlsEnabled = uiState.downloadEngineReady && !uiState.isLoading &&
            !uiState.isTranslatingForShare

        TrimSection(
            trim = uiState.trim,
            enabled = controlsEnabled,
            onToggle = onTrimToggle,
            onStartChange = onTrimStartChange,
            onEndChange = onTrimEndChange
        )

        val downloadEnabled = controlsEnabled && uiState.url.isNotBlank() &&
            !uiState.trimBlocksDownload
        Button(
            onClick = onDownloadVideo,
            enabled = downloadEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = BabakCastColors.PrimaryAccent,
                contentColor = BabakCastColors.BackgroundPrimary,
                disabledContainerColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.3f),
                disabledContentColor = BabakCastColors.BackgroundPrimary.copy(alpha = 0.5f)
            )
        ) {
            Text(
                stringResource(R.string.download_video),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            )
        }

        SplitSizeSlider(
            valueMb = uiState.splitSizeMb,
            minMb = SplitSize.MIN_MB,
            maxMb = SplitSize.MAX_MB,
            enabled = downloadEnabled,
            onValueChange = onSplitSizeChange
        )

        OutlinedButton(
            onClick = onDownloadSplitVideo,
            enabled = downloadEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BabakCastColors.PrimaryAccent,
                disabledContentColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.3f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = downloadEnabled).copy(
                brush = SolidColor(
                    if (downloadEnabled) {
                        BabakCastColors.PrimaryAccent.copy(alpha = 0.5f)
                    } else {
                        BabakCastColors.PrimaryAccent.copy(alpha = 0.2f)
                    }
                )
            )
        ) {
            Text(
                stringResource(R.string.download_split_video, uiState.splitSizeMb),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            )
        }

        val isXUrl = XUrlExtractor.isXUrl(uiState.url)
        AnimatedVisibility(
            visible = isXUrl,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val isXActionEnabled = !uiState.isLoading && uiState.url.isNotBlank() &&
                !uiState.isTranslatingForShare
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val isAllMediaEnabled = uiState.downloadEngineReady && isXActionEnabled &&
                    !uiState.trimBlocksAllMedia
                OutlinedButton(
                    onClick = onDownloadAllMedia,
                    enabled = isAllMediaEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BabakCastColors.PrimaryAccent,
                        disabledContentColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.3f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = isAllMediaEnabled).copy(
                        brush = SolidColor(
                            if (isAllMediaEnabled) {
                                BabakCastColors.PrimaryAccent.copy(alpha = 0.5f)
                            } else {
                                BabakCastColors.PrimaryAccent.copy(alpha = 0.2f)
                            }
                        )
                    )
                ) {
                    Text(
                        stringResource(R.string.download_all_media),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyTweetText,
                        enabled = isXActionEnabled && !uiState.isFetchingTweetText,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    ) {
                        if (uiState.isFetchingTweetText) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = BabakCastColors.PrimaryAccent
                            )
                        } else {
                            Text(
                                stringResource(R.string.copy_text),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onShareTweetText,
                        enabled = isXActionEnabled && !uiState.isFetchingTweetText,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BabakCastColors.SecondaryAccent,
                            disabledContentColor = BabakCastColors.SecondaryAccent.copy(alpha = 0.3f)
                        )
                    ) {
                        if (uiState.isFetchingTweetText) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = BabakCastColors.SecondaryAccent
                            )
                        } else {
                            Text(
                                stringResource(R.string.share_text),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        AudioActionButtons(
            uiState = uiState,
            onDownloadAudio = onDownloadAudio,
            onDownloadSplitAudio = onDownloadSplitAudio
        )

        AnimatedVisibility(
            visible = uiState.supportsSummarize,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.summary_length_label),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val lengths = SummaryLength.entries
                    lengths.forEachIndexed { index, length ->
                        SegmentedButton(
                            selected = uiState.summaryLength == length,
                            onClick = { onSummaryLengthChange(length) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = lengths.size
                            ),
                            enabled = !uiState.isLoading && !uiState.isTranslatingForShare,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.15f),
                                activeContentColor = BabakCastColors.PrimaryAccent,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledActiveContainerColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.08f),
                                disabledActiveContentColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
stringResource(length.labelRes()),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }

        val summarizeEnabled = uiState.downloadEngineReady &&
            !uiState.isLoading &&
            uiState.url.isNotBlank() &&
            uiState.supportsSummarize &&
            !uiState.isTranslatingForShare
        OutlinedButton(
            onClick = onSummarize,
            enabled = summarizeEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = summarizeEnabled).copy(
                brush = SolidColor(
                    if (summarizeEnabled) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    }
                )
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isSummarizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = BabakCastColors.PrimaryAccent
                    )
                }
                Text(
                    if (uiState.isSummarizing) {
                        stringResource(R.string.summarize_in_progress)
                    } else if (!uiState.supportsSummarize) {
                        stringResource(R.string.summarize_youtube_only)
                    } else {
                        stringResource(R.string.summarize_transcript)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}
