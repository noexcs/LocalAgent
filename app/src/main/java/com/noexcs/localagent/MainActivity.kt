package com.noexcs.localagent

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.noexcs.localagent.agent.AgentViewModel
import com.noexcs.localagent.data.FileChatHistoryProvider
import com.noexcs.localagent.data.MemoryManager
import com.noexcs.localagent.data.SettingsManager
import com.noexcs.localagent.ui.ChatScreen
import com.noexcs.localagent.ui.ScheduledTaskListScreen
import com.noexcs.localagent.ui.SettingsScreen
import com.noexcs.localagent.ui.theme.LocalagentTheme

private const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

private sealed class Screen {
    data object Chat : Screen()
    data object Settings : Screen()
    data object ScheduledTasks : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalagentTheme {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    var hasPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, TERMUX_RUN_COMMAND_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Toast.makeText(
                context,
                "RUN_COMMAND permission is required. Grant it in Settings → Apps → localagent → Permissions.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (hasPermission) {
        val appContext = context.applicationContext
        val memoryManager = remember { MemoryManager(appContext) }
        val settingsManager = remember { SettingsManager(appContext) }
        val conversationRepository = remember { FileChatHistoryProvider(appContext) }
        val viewModel = remember {
            AgentViewModel(appContext, memoryManager, settingsManager, conversationRepository)
        }

        var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }

        // Back press on Settings/ScheduledTasks returns to Chat instead of exiting
        BackHandler(enabled = currentScreen !is Screen.Chat) {
            currentScreen = Screen.Chat
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val duration = 250
                when {
                    targetState is Screen.Settings || targetState is Screen.ScheduledTasks -> {
                        (slideInHorizontally(tween(duration)) { it / 3 } + fadeIn(tween(duration)))
                            .togetherWith(slideOutHorizontally(tween(duration)) { -it / 3 } + fadeOut(tween(duration)))
                    }
                    targetState is Screen.Chat -> {
                        (slideInHorizontally(tween(duration)) { -it / 3 } + fadeIn(tween(duration)))
                            .togetherWith(slideOutHorizontally(tween(duration)) { it / 3 } + fadeOut(tween(duration)))
                    }
                    else -> fadeIn(tween(duration)).togetherWith(fadeOut(tween(duration)))
                }
            },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                Screen.Chat -> ChatScreen(
                    viewModel = viewModel,
                    conversationRepository = conversationRepository,
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onOpenScheduledTasks = { currentScreen = Screen.ScheduledTasks },
                )
                Screen.Settings -> SettingsScreen(
                    settingsManager = settingsManager,
                    memoryManager = memoryManager,
                    onBack = { currentScreen = Screen.Chat }
                )
                Screen.ScheduledTasks -> ScheduledTaskListScreen(
                    onBack = { currentScreen = Screen.Chat }
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.permission_required),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(TERMUX_RUN_COMMAND_PERMISSION) }) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        }
    }
}
