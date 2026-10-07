package com.cocode.babakcast.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.cocode.babakcast.ui.theme.BabakCastColors
import androidx.compose.ui.res.stringResource
import com.cocode.babakcast.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.settings_title),
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // AI Providers Section
            SectionHeader(title = stringResource(R.string.settings_ai_providers))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                uiState.providers.forEachIndexed { index, providerState ->
                    ProviderCard(
                        providerState = providerState,
                        onClick = { viewModel.selectProvider(providerState.provider) },
                        isFirst = index == 0,
                        isLast = index == uiState.providers.lastIndex
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            GeneralSettingsSection(
                defaultLanguage = uiState.defaultLanguage,
                adaptiveSummaryLength = uiState.adaptiveSummaryLength,
                defaultSummaryLength = uiState.defaultSummaryLength,
                autoPlayNext = uiState.autoPlayNext,
                onLanguageChange = viewModel::updateDefaultLanguage,
                onAdaptiveLengthChange = viewModel::updateAdaptiveSummaryLength,
                onSummaryLengthChange = viewModel::updateDefaultSummaryLength,
                onAutoPlayChange = viewModel::updateAutoPlayNext
            )

            Spacer(modifier = Modifier.height(32.dp))

            // About Section: the About screen holds the version, links, credits and privacy promises
            SectionHeader(title = stringResource(R.string.settings_about))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                SettingsRow(
                    label = stringResource(R.string.about_title),
                    value = viewModel.installedVersionName,
                    onClick = onNavigateToAbout,
                    isFirst = true,
                    isLast = true
                )
            }
        }
    }

    // Provider Configuration Dialog
    if (uiState.showProviderDialog && uiState.selectedProvider != null) {
        val selectedProvider = uiState.selectedProvider!!
        ProviderConfigDialog(
            provider = selectedProvider,
            modelsToShow = viewModel.getModelsForProvider(selectedProvider),
            modelsLoading = uiState.modelsLoading,
            modelsError = uiState.modelsError,
            apiKey = uiState.editingApiKey,
            apiUrl = uiState.editingApiUrl,
            selectedModel = uiState.editingModel,
            showModelDropdown = uiState.showModelDropdown,
            onApiKeyChange = viewModel::updateEditingApiKey,
            onApiUrlChange = viewModel::updateEditingApiUrl,
            onModelChange = viewModel::updateEditingModel,
            onModelDropdownVisibleChange = viewModel::setModelDropdownVisible,
            onSave = viewModel::saveProviderConfig,
            onDelete = { viewModel.deleteProviderApiKey(selectedProvider.id) },
            onDismiss = viewModel::dismissProviderDialog,
            showUrlField = selectedProvider.id == "azure-openai",
            hasExistingKey = uiState.editingApiKey.isNotBlank()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderConfigDialog(
    provider: com.cocode.babakcast.data.model.Provider,
    modelsToShow: List<String>,
    modelsLoading: Boolean,
    modelsError: String?,
    apiKey: String,
    apiUrl: String,
    selectedModel: String,
    showModelDropdown: Boolean,
    onApiKeyChange: (String) -> Unit,
    onApiUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onModelDropdownVisibleChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    showUrlField: Boolean,
    hasExistingKey: Boolean
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = provider.display_name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                ModelSelectorSection(
                    modelsToShow = modelsToShow,
                    modelsLoading = modelsLoading,
                    modelsError = modelsError,
                    selectedModel = selectedModel,
                    showModelDropdown = showModelDropdown,
                    onModelChange = onModelChange,
                    onDropdownVisibleChange = onModelDropdownVisibleChange
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_api_key),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        placeholder = { 
                            Text(
                                stringResource(R.string.settings_api_key_placeholder),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BabakCastColors.PrimaryAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            cursorColor = BabakCastColors.PrimaryAccent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        shape = MaterialTheme.shapes.small
                    )
                }

                if (showUrlField) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_api_endpoint),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = onApiUrlChange,
                            placeholder = { 
                                Text(
                                    "https://YOUR_RESOURCE.openai.azure.com/...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                ) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BabakCastColors.PrimaryAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                cursorColor = BabakCastColors.PrimaryAccent
                            ),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            shape = MaterialTheme.shapes.small
                        )
                        Text(
                            text = stringResource(R.string.settings_azure_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    ) {
                        Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BabakCastColors.PrimaryAccent,
                            contentColor = BabakCastColors.BackgroundPrimary
                        )
                    ) {
                        Text(
                            stringResource(R.string.action_save),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                if (hasExistingKey) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = BabakCastColors.Error
                        )
                    ) {
                        Text(
                            stringResource(R.string.settings_remove_configuration),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }
}
