package com.noexcs.localagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.noexcs.localagent.R
import com.noexcs.localagent.data.MemoryManager
import com.noexcs.localagent.data.SettingsManager
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
    var systemPrompt by remember { mutableStateOf(settingsManager.userSystemPrompt) }
    var memory by remember { mutableStateOf(memoryManager.read()) }
    var providerType by remember { mutableStateOf(settingsManager.providerType) }
    var baseUrl by remember { mutableStateOf(settingsManager.baseUrl) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var model by remember { mutableStateOf(settingsManager.model) }
    var selectedLanguage by remember { mutableStateOf(settingsManager.language) }
    var toolConfirmMode by remember { mutableStateOf(settingsManager.toolConfirmMode) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val savedMsg = stringResource(R.string.settings_saved)

    fun save() {
        settingsManager.userSystemPrompt = systemPrompt
        settingsManager.providerType = providerType
        settingsManager.baseUrl = baseUrl
        settingsManager.apiKey = apiKey
        settingsManager.model = model
        memoryManager.write(memory)
        settingsManager.toolConfirmMode = toolConfirmMode
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

    fun markChanged() { hasUnsavedChanges = true }

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
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
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
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Tool confirmation section
            SectionCard(
                title = stringResource(R.string.section_tool_confirm),
                subtitle = stringResource(R.string.section_tool_confirm_subtitle)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = toolConfirmMode == "ask",
                        onClick = { toolConfirmMode = "ask"; markChanged() },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) {
                        Text(stringResource(R.string.tool_confirm_ask), style = MaterialTheme.typography.labelMedium)
                    }
                    SegmentedButton(
                        selected = toolConfirmMode == "always_allow",
                        onClick = { toolConfirmMode = "always_allow"; markChanged() },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) {
                        Text(stringResource(R.string.tool_confirm_always_allow), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Provider section
            SectionCard(
                title = stringResource(R.string.section_provider),
                subtitle = stringResource(R.string.section_provider_subtitle)
            ) {
                OutlinedTextField(
                    value = providerType,
                    onValueChange = { providerType = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_label)) },
                    placeholder = { Text("OpenAI-Compatible") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = settingsFieldColors()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.base_url_label)) },
                    placeholder = { Text("https://api.openai.com") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = settingsFieldColors()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_key_label)) },
                    placeholder = { Text("sk-...") },
                    shape = RoundedCornerShape(12.dp),
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
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.model_label)) },
                    placeholder = { Text("gpt-4o") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = settingsFieldColors()
                )
            }

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
                    shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    minLines = 8,
                    colors = settingsFieldColors()
                )
            }

            // Unsaved indicator
            if (hasUnsavedChanges) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
