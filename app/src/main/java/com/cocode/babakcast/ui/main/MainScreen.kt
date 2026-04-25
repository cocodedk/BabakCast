package com.cocode.babakcast.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.cocode.babakcast.domain.split.SplitMode
import com.cocode.babakcast.ui.downloads.DownloadsTab
import com.cocode.babakcast.ui.theme.BabakCastColors
import com.cocode.babakcast.util.ShareHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val shareHelper = remember(context) { ShareHelper(context.applicationContext) }
    var pendingAudioShare by remember { mutableStateOf<ShareRequest.AudioTwoStep?>(null) }
    val audioShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val textShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = pendingAudioShare
        if (pending != null) {
            val shareIntent = shareHelper.buildShareFilesChooser(
                files = pending.files,
                mimeType = pending.mimeType,
                title = pending.title,
                text = pending.caption
            )
            if (shareIntent != null) {
                audioShareLauncher.launch(shareIntent)
            }
            pendingAudioShare = null
        }
    }

    // Apply shared URL when user shares from YouTube, X, or other app into BabakCast
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

    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { request ->
            when (request) {
                is ShareRequest.AudioTwoStep -> {
                    if (request.caption.isBlank()) {
                        val shareIntent = shareHelper.buildShareFilesChooser(
                            files = request.files,
                            mimeType = request.mimeType,
                            title = request.title,
                            text = null
                        )
                        if (shareIntent != null) {
                            audioShareLauncher.launch(shareIntent)
                        }
                    } else {
                        pendingAudioShare = request
                        val textIntent = shareHelper.buildShareTextChooser(
                            text = request.caption,
                            title = "Share title"
                        )
                        textShareLauncher.launch(textIntent)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.tweetTextEvents.collect { event ->
            when (event) {
                is TweetTextEvent.Copied -> {
                    clipboardManager.setPlainText(event.text)
                    snackbarHostState.showSnackbar(
                        message = "Tweet text copied",
                        duration = SnackbarDuration.Short
                    )
                }
                is TweetTextEvent.Share -> {
                    val intent = shareHelper.buildShareTextChooser(event.text, "Share Tweet Text")
                    textShareLauncher.launch(intent)
                }
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

                UrlInputSection(
                    url = uiState.url,
                    isLoading = uiState.isLoading,
                    onUrlChange = viewModel::updateUrl,
                    onClear = { viewModel.updateUrl("") }
                )

                Spacer(modifier = Modifier.height(28.dp))

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

                ActionButtonsSection(
                    uiState = uiState,
                    onDownloadVideo = viewModel::downloadVideo,
                    onDownloadSplitVideo = viewModel::downloadSplitVideo,
                    onSplitSizeChange = viewModel::updateSplitSizeMb,
                    onDownloadAllMedia = viewModel::downloadAllXMedia,
                    onCopyTweetText = viewModel::fetchAndCopyTweetText,
                    onShareTweetText = viewModel::fetchAndShareTweetText,
                    onDownloadAudio = viewModel::downloadAudio,
                    onSummarize = viewModel::generateSummary,
                    onSummaryLengthChange = viewModel::updateSummaryLength
                )

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
                    if (uiState.isProgressIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = BabakCastColors.PrimaryAccent,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { safeProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = BabakCastColors.PrimaryAccent,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                    uiState.loadingMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (uiState.isProgressIndeterminate) "Working..." else "${(safeProgress * 100).roundToInt()}%",
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
                    SummarySection(
                        summary = summary,
                        onCopySummary = {
                            scope.launch {
                                clipboardManager.setPlainText(summary)
                                snackbarHostState.showSnackbar(
                                    message = "Copied to clipboard",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        onShareSummaryAsFile = viewModel::shareSummaryAsFile,
                        onShareSummary = viewModel::shareSummary
                    )
                }
            }

            // Error Display
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.error?.let { error ->
                    ErrorDialog(
                        error = error,
                        onDismiss = viewModel::clearError
                    )
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

    uiState.splitChoicePrompt?.let { prompt ->
        SplitModeDialog(
            prompt = prompt,
            onChoice = viewModel::chooseSplitMode,
            onDismiss = { viewModel.chooseSplitMode(SplitMode.SIZE_16MB) }
        )
    }
}

private suspend fun androidx.compose.ui.platform.Clipboard.setPlainText(text: String) {
    setClipEntry(
        ClipEntry(
            ClipData.newPlainText("text", text)
        )
    )
}
