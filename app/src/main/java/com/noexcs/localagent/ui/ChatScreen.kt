package com.noexcs.localagent.ui

import ai.koog.prompt.message.Message.Role
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.noexcs.localagent.R
import com.noexcs.localagent.agent.AgentViewModel
import com.noexcs.localagent.data.FileChatHistoryProvider
import com.noexcs.localagent.data.MessageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AgentViewModel,
    conversationRepository: FileChatHistoryProvider,
    onOpenSettings: () -> Unit = {},
    onOpenScheduledTasks: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }

    // Set up callback to refresh conversation list when messages are updated
    LaunchedEffect(Unit) {
        viewModel.onConversationUpdated = {
            refreshTrigger++
        }
    }

    // Back press closes drawer instead of exiting
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                repository = conversationRepository,
                onLoad = { id ->
                    viewModel.loadConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                refreshTrigger = refreshTrigger
            )
        }
    ) {
        ChatContent(
            viewModel = viewModel,
            conversationRepository = conversationRepository,
            refreshTrigger = refreshTrigger,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenSettings = onOpenSettings,
            onOpenScheduledTasks = onOpenScheduledTasks,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    viewModel: AgentViewModel,
    conversationRepository: FileChatHistoryProvider,
    refreshTrigger: Int,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScheduledTasks: () -> Unit,
) {
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMsg = stringResource(R.string.copied)

    // Refresh conversation list when trigger changes
    LaunchedEffect(refreshTrigger) {
        // This will cause the drawer content to recompose with fresh data
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val lastAssistantIndex = if (!isLoading) {
        messages.indices.lastOrNull { messages[it].role == Role.Assistant }
    } else null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.conversations),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.newConversation() }) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = stringResource(R.string.new_chat),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenScheduledTasks) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.scheduled_tasks),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                .consumeWindowInsets(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // With reverseLayout, index 0 is at the bottom (most recent)
                if (isLoading) {
                    item { ThinkingIndicator() }
                }

                itemsIndexed(
                    messages.asReversed(),
                    key = { index, _ -> messages.size - 1 - index }) { index, message ->
                    val originalIndex = messages.size - 1 - index

                    // Show action buttons before the message (visually below in reversed layout)
                    if (originalIndex == lastAssistantIndex) {
                        AssistantActions(
                            onCopy = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "message",
                                        message.content
                                    )
                                )
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        copiedMsg,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onRegenerate = { /* TODO */ }
                        )
                    }

                    MessageBubble(message)
                }

                if (messages.isEmpty() && !isLoading) {
                    item { EmptyState() }
                }
            }

            // Error
            AnimatedVisibility(visible = error != null) {
                error?.let { errorMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Input area
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                stringResource(R.string.message_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (input.isNotBlank() && !isLoading) {
                                    viewModel.sendMessage(input.trim())
                                    input = ""
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            }
                        )
                    )
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendMessage(input.trim())
                                input = ""
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        },
                        enabled = input.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.4f
                            )
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.send),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantActions(
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistantActionButton(icon = {
            Icon(
                Icons.Default.CopyAll,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }, onClick = onCopy)
        AssistantActionButton(
            icon = {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            onClick = onRegenerate
        )
    }
}

@Composable
private fun AssistantActionButton(
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled = false),
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//            Surface(
//                shape = CircleShape,
//                color = MaterialTheme.colorScheme.primaryContainer,
//                modifier = Modifier.size(72.dp)
//            ) {
//                Box(contentAlignment = Alignment.Center) {
//                    Icon(
//                        Icons.Filled.,
//                        contentDescription = null,
//                        modifier = Modifier.size(36.dp),
//                        tint = MaterialTheme.colorScheme.onPrimaryContainer
//                    )
//                }
//            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.thinking),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun MessageBubble(message: MessageViewModel) {
    when (message.role) {
        Role.User -> UserBubble(message.content)
        Role.Assistant -> AssistantBubble(message.content)
        Role.System -> AssistantBubble(message.content)
        Role.Reasoning -> AssistantBubble(message.content)
        Role.Tool -> ToolResultBubble(message.content)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantBubble(content: String) {
    Markdown(
        content = content,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeText = MaterialTheme.colorScheme.onSurfaceVariant,
            codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        ),
        typography = markdownTypography(
            text = MaterialTheme.typography.bodyMedium,
            code = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        ),
    )
}

@Composable
private fun ToolResultBubble(content: String) {
    var expanded by remember { mutableStateOf(false) }
    val isLong = content.length > 50
    val preview = if (isLong && !expanded) content.take(50) + "\u2026" else content

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 0.dp)
            .then(if (isLong) Modifier.clickable { expanded = !expanded } else Modifier)
            .animateContentSize()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("🔧", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = preview,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
