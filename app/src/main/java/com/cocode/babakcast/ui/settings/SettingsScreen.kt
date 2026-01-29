package com.cocode.babakcast.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.cocode.babakcast.ui.theme.BabakCastColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
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
                            contentDescription = "Back",
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
            SectionHeader(title = "AI Providers")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                uiState.providers.forEachIndexed { index, providerState ->
                    ProviderRow(
                        providerState = providerState,
                        onClick = { viewModel.selectProvider(providerState.provider) },
                        isFirst = index == 0,
                        isLast = index == uiState.providers.lastIndex
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Defaults Section
            SectionHeader(title = "Defaults")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SUMMARY / TRANSLATION LANGUAGE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.defaultLanguage,
                        onValueChange = viewModel::updateDefaultLanguage,
                        placeholder = {
                            Text(
                                "e.g. en, es, fa, German",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BabakCastColors.PrimaryAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = BabakCastColors.PrimaryAccent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        shape = MaterialTheme.shapes.medium
                    )
                }
                SettingsRow(
                    label = "Summary Style",
                    value = "Bullet Points",
                    onClick = { /* TODO */ },
                    isFirst = false,
                    isLast = true
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // About Section
            SectionHeader(title = "About")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Your API key is sent directly to the provider you configure. BabakCast does not proxy or store your data.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "View on GitHub →",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = BabakCastColors.SecondaryAccent,
                    modifier = Modifier.clickable { /* TODO: Open GitHub */ }
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
            onToggleModelDropdown = viewModel::toggleModelDropdown,
            onDismissModelDropdown = viewModel::dismissModelDropdown,
            onSave = viewModel::saveProviderConfig,
            onDelete = { viewModel.deleteProviderApiKey(selectedProvider.id) },
            onDismiss = viewModel::dismissProviderDialog,
            showUrlField = selectedProvider.id == "azure-openai",
            hasExistingKey = uiState.editingApiKey.isNotBlank()
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun ProviderRow(
    providerState: ProviderState,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val shape = when {
        isFirst && isLast -> MaterialTheme.shapes.medium
        isFirst -> MaterialTheme.shapes.medium.copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
        )
        isLast -> MaterialTheme.shapes.medium.copy(
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = providerState.provider.display_name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (providerState.hasApiKey) {
                        "${providerState.selectedModel} · ${providerState.maskedApiKey}"
                    } else {
                        "Not configured"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = if (providerState.hasApiKey) 
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (providerState.hasApiKey) 
                            BabakCastColors.Success
                        else 
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val shape = when {
        isFirst && isLast -> MaterialTheme.shapes.medium
        isFirst -> MaterialTheme.shapes.medium.copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
        )
        isLast -> MaterialTheme.shapes.medium.copy(
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    onToggleModelDropdown: () -> Unit,
    onDismissModelDropdown: () -> Unit,
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
                // Header
                Text(
                    text = provider.display_name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Model Selection
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
                        onExpandedChange = { onToggleModelDropdown() }
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
                            ExposedDropdownMenu(
                                expanded = showModelDropdown,
                                onDismissRequest = onDismissModelDropdown,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                modelsToShow.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = model,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 14.sp
                                                ),
                                                color = if (model == selectedModel)
                                                    BabakCastColors.PrimaryAccent
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        },
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

                // API Key field
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "API Key",
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
                                "Enter your API key",
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

                // API URL field (only for Azure OpenAI)
                if (showUrlField) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "API Endpoint",
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
                            text = "Replace YOUR_RESOURCE and YOUR_DEPLOYMENT with your Azure values",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Action buttons
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
                        Text("Cancel", style = MaterialTheme.typography.bodyMedium)
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
                            "Save",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Delete option
                if (hasExistingKey) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = BabakCastColors.Error
                        )
                    ) {
                        Text(
                            "Remove Configuration",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }
}
