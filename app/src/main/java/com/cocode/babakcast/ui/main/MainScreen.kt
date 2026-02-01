package com.cocode.babakcast.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.activity.compose.LocalActivity
import android.content.ClipData
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cocode.babakcast.ui.downloads.DownloadsTab
import com.cocode.babakcast.ui.theme.BabakCastColors
import com.cocode.babakcast.util.AppError
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val safeProgress = uiState.progress.coerceIn(0f, 1f)
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Apply shared URL when user shares from YouTube (or other app) into BabakCast
    val activity = LocalActivity.current as? ComponentActivity
    val shareIntentViewModel = activity?.let { viewModel<ShareIntentViewModel>(viewModelStoreOwner = it) }
    LaunchedEffect(shareIntentViewModel) {
        shareIntentViewModel?.pendingSharedUrl?.collect { pendingUrl ->
            if (pendingUrl != null) {
                viewModel.updateUrl(pendingUrl)
                shareIntentViewModel.clearPendingUrl()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        Text(
                            "BabakCast",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp
                            )
                        ) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = BabakCastColors.PrimaryAccent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = BabakCastColors.PrimaryAccent
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Home") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Downloads") }
                    )
                }
            }
        }
    ) { paddingValues ->
        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
            
            // URL Input Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "YOUTUBE URL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = viewModel::updateUrl,
                    placeholder = { 
                        Text(
                            "Paste video link here",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isLoading,
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

            Spacer(modifier = Modifier.height(28.dp))

            // Download engine status: show "Preparing..." or error until ready
            when {
                uiState.downloadEngineError != null -> {
                    Text(
                        text = "Download unavailable: ${uiState.downloadEngineError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                !uiState.downloadEngineReady -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BabakCastColors.PrimaryAccent
                        )
                        Text(
                            text = "Preparing download engine…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download Video Button - Primary action
                Button(
                    onClick = viewModel::downloadVideo,
                    enabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank(),
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
                        "Download Video",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    )
                }

                // Download Audio Button - Secondary action
                OutlinedButton(
                    onClick = viewModel::downloadAudio,
                    enabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank()).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank())
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isDownloadingAudio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = BabakCastColors.PrimaryAccent
                            )
                        }
                        Text(
                            if (uiState.isDownloadingAudio) "Downloading Audio…" else "Download Audio",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                // Summarize Transcript Button - Secondary action
                OutlinedButton(
                    onClick = viewModel::generateSummary,
                    enabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank()).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (uiState.downloadEngineReady && !uiState.isLoading && uiState.url.isNotBlank())
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                            if (uiState.isSummarizing) "Summarizing…" else "Summarize Transcript",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }

            // Progress Indicator
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { safeProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = BabakCastColors.PrimaryAccent,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Text(
                        text = "${(safeProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Summary Display
            AnimatedVisibility(
                visible = uiState.summary != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.summary?.let { summary ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "SUMMARY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            if (summary.length > 8000) {
                                Text(
                                    text = "Long summary detected — Copy is recommended instead of Share.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            clipboardManager.setPlainText(summary)
                                            snackbarHostState.showSnackbar(
                                                message = "Copied to clipboard",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(
                                        "Copy",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                                TextButton(
                                    onClick = viewModel::shareSummaryAsFile,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(
                                        "Share as file",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                                TextButton(
                                    onClick = viewModel::shareSummary,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = BabakCastColors.SecondaryAccent
                                    )
                                ) {
                                    Text(
                                        "Share →",
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
            }

            // Error Display
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.error?.let { error: AppError ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = BabakCastColors.Error.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = error.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = BabakCastColors.Error
                            )
                            
                            Text(
                                text = error.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            
                            error.fixHint?.let { hint ->
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = viewModel::clearError,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(
                                        "Dismiss",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            DownloadsTab(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

private suspend fun androidx.compose.ui.platform.Clipboard.setPlainText(text: String) {
    setClipEntry(
        ClipEntry(
            ClipData.newPlainText("summary", text)
        )
    )
}
