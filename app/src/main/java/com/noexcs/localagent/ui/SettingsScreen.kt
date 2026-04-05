package com.noexcs.localagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
//import androidx.compose.material3.ExposedDropdownMenuBoxScope.menuAnchor
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ai.koog.prompt.llm.LLModel
import androidx.activity.compose.BackHandler
//import androidx.compose.material3.ExposedDropdownMenuBoxScope.menuAnchor
import androidx.compose.material3.MenuAnchorType
import com.noexcs.localagent.R
import com.noexcs.localagent.data.MemoryManager
import com.noexcs.localagent.data.SettingsManager
import com.noexcs.localagent.data.getPredefinedProviderModels
import com.noexcs.localagent.data.getOnlineModels
import com.noexcs.localagent.data.getProviders
import kotlinx.coroutines.launch

private data class LanguageOption(val tag: String, val labelRes: Int)

private val languageOptions = listOf(
    LanguageOption("", R.string.language_system),
    LanguageOption("en", R.string.language_en),
    LanguageOption("zh-Hans", R.string.language_zh_hans),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    memoryManager: MemoryManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var systemPrompt by remember { mutableStateOf(settingsManager.userSystemPrompt) }
    var memory by remember { mutableStateOf(memoryManager.read()) }
    var providerType by remember { mutableStateOf(settingsManager.providerType) }
    var baseUrl by remember { mutableStateOf(settingsManager.baseUrl) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var model by remember { mutableStateOf(settingsManager.model) }
    var selectedLanguage by remember { mutableStateOf(settingsManager.language) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    var providerExpanded by remember { mutableStateOf(false) }

    var modelExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val savedMsg = stringResource(R.string.settings_saved)

    var availableModels by remember { mutableStateOf<List<LLModel>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelsLoadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(providerType) {
        if (providerType != null)
            availableModels = getPredefinedProviderModels(providerType!!)
    }

    BackHandler(enabled = hasUnsavedChanges) {
        if (hasUnsavedChanges)
            showExitDialog = true
    }

    // Function to refresh models from network
    fun refreshModelsFromNetwork() {
        if (providerType == null)
            return
        scope.launch {
            isLoadingModels = true
            modelsLoadError = null
            try {
                val models =
                    getOnlineModels(context, providerType!!, apiKey!!, baseUrl!!)
                availableModels = if (models.isEmpty()) {
                    // Fallback to local models if network returns empty
                    getPredefinedProviderModels(providerType!!).map { it }
                } else {
                    models.map { it }
                }

                // Auto-select first model if current is empty
                if (availableModels.isNotEmpty() && model == null) {
                    model = availableModels.first().id
                }
            } catch (e: Exception) {
                modelsLoadError = e.message ?: "Failed to load models"
            } finally {
                isLoadingModels = false
            }
        }
    }

    fun save() {
        settingsManager.userSystemPrompt = systemPrompt
        settingsManager.providerType = providerType
        settingsManager.baseUrl = baseUrl
        settingsManager.apiKey = apiKey
        settingsManager.model = model
        memoryManager.write(memory)
        if (selectedLanguage != settingsManager.language) {
            settingsManager.language = selectedLanguage
        }
        hasUnsavedChanges = false
        scope.launch {
            snackbarHostState.showSnackbar(
                message = savedMsg,
                duration = SnackbarDuration.Short
            )
        }
    }

    fun markChanged() {
        hasUnsavedChanges = true
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) showExitDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { save() },
                        enabled = hasUnsavedChanges,
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.save),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // System Prompt section
            SectionCard(
                title = stringResource(R.string.section_system_prompt),
                subtitle = stringResource(R.string.section_system_prompt_subtitle)
            ) {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.system_prompt_placeholder)) },

                    minLines = 4,
                    maxLines = 10,
                    colors = settingsFieldColors()
                )
            }

            // Memory section
            SectionCard(
                title = stringResource(R.string.section_memory),
                subtitle = stringResource(R.string.section_memory_subtitle)
            ) {
                OutlinedTextField(
                    value = memory,
                    onValueChange = { memory = it; markChanged() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp),
                    placeholder = { Text(stringResource(R.string.memory_placeholder)) },
                    minLines = 8,
                    colors = settingsFieldColors()
                )
            }


            // Provider section
            SectionCard(
                title = stringResource(R.string.section_provider),
                subtitle = stringResource(R.string.section_provider_subtitle)
            ) {
                // Provider dropdown
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = providerType?.display ?: "Select Provider",
                        onValueChange = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                        label = { Text(stringResource(R.string.provider_label)) },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    )
                    ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                        getProviders().forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(text = provider.display) },
                                onClick = {
                                    providerType = provider
                                    providerExpanded = false
                                    markChanged()
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey ?: "",
                    onValueChange = { apiKey = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_key_label)) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (apiKeyVisible) R.string.hide else R.string.show)
                            )
                        }
                    },
                    colors = settingsFieldColors()
                )

                // Model dropdown with refresh button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded && availableModels.isNotEmpty(),
                        onExpandedChange = { modelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = model?:"",
                            onValueChange = { },
                            modifier = Modifier
                                .fillMaxWidth(1f)
                                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                            label = { Text(stringResource(R.string.model_label)) },
                            readOnly = true,
                            trailingIcon = {
                                if (isLoadingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (availableModels.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select model",
                                        modifier = Modifier.rotate(if (modelExpanded) 180f else 0f)
                                    )
                                }
                            },
                            colors = settingsFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            availableModels.forEach { modelItem ->
                                DropdownMenuItem(
                                    text = { Text(modelItem.id) },
                                    onClick = {
                                        model = modelItem.id
                                        modelExpanded = false
                                        markChanged()
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    // Refresh button
                    IconButton(
                        onClick = { refreshModelsFromNetwork() },
                        modifier = Modifier.size(48.dp),
                        enabled = !isLoadingModels
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh models",
                            tint = if (isLoadingModels) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.rotate(if (isLoadingModels) 180f else 0f)
                        )
                    }
                }
            }

            // Language section
            SectionCard(
                title = stringResource(R.string.section_language),
                subtitle = stringResource(R.string.section_language_subtitle)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    languageOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = selectedLanguage == option.tag,
                            onClick = { selectedLanguage = option.tag; markChanged() },
                            shape = SegmentedButtonDefaults.itemShape(index, languageOptions.size),
                        ) {
                            Text(
                                stringResource(option.labelRes),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Unsaved indicator
            if (hasUnsavedChanges) {
                Surface(

                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            stringResource(R.string.unsaved_changes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Exit confirmation
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_message)) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onBack() }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            content()
        }
    }
}
