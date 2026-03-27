package com.noexcs.localagent.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.core.content.ContextCompat
import com.noexcs.localagent.R
import com.noexcs.localagent.data.task.ScheduledTask
import com.noexcs.localagent.data.task.ScheduledTaskRepository
import com.noexcs.localagent.data.task.TaskFrequency
import com.noexcs.localagent.scheduler.TaskScheduler
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScheduledTaskRepository(context.applicationContext) }
    val scheduler = remember { TaskScheduler(context.applicationContext) }
    var tasks by remember { mutableStateOf(repo.listAll()) }
    var showSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }

    // Request POST_NOTIFICATIONS runtime permission (required on API 33+)
    var notificationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationPermissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Try to schedule; if permission missing, open system settings and show a toast. */
    fun trySchedule(task: ScheduledTask) {
        if (!scheduler.schedule(task)) {
            Toast.makeText(context, context.getString(R.string.exact_alarm_permission_needed), Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.scheduled_tasks)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingTask = null; showSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onClick = { editingTask = task; showSheet = true },
                        onToggle = { enabled ->
                            val updated = task.copy(enabled = enabled)
                            repo.save(updated)
                            if (enabled) trySchedule(updated) else scheduler.cancel(task.id)
                            tasks = repo.listAll()
                        },
                        onDelete = {
                            scheduler.cancel(task.id)
                            repo.delete(task.id)
                            tasks = repo.listAll()
                        }
                    )
                }
            }
        }
    }

    if (showSheet) {
        TaskEditSheet(
            task = editingTask,
            onDismiss = { showSheet = false },
            onSave = { task ->
                repo.save(task)
                if (task.enabled) trySchedule(task) else scheduler.cancel(task.id)
                tasks = repo.listAll()
                showSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: ScheduledTask,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val freqLabel = when (task.frequency) {
        TaskFrequency.DAILY -> stringResource(R.string.freq_daily)
        TaskFrequency.WEEKDAYS -> stringResource(R.string.freq_weekdays)
        TaskFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
        TaskFrequency.ONCE -> stringResource(R.string.freq_once)
    }
    val timeStr = "%02d:%02d".format(task.hour, task.minute)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        ElevatedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        ) {
            ListItem(
                headlineContent = {
                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                },
                supportingContent = {
                    Text(
                        "$freqLabel · $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(checked = task.enabled, onCheckedChange = onToggle)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditSheet(
    task: ScheduledTask?,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var frequency by remember { mutableStateOf(task?.frequency ?: TaskFrequency.DAILY) }
    var hour by remember { mutableIntStateOf(task?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(task?.minute ?: 0) }
    var prompt by remember { mutableStateOf(task?.prompt ?: "") }
    var notifyEnabled by remember { mutableStateOf(task?.notifyEnabled ?: true) }
    var showTimePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(if (task == null) R.string.add_task else R.string.edit_task),
                style = MaterialTheme.typography.titleMedium
            )

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Frequency
            Text(stringResource(R.string.task_frequency), style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = TaskFrequency.entries
                options.forEachIndexed { index, freq ->
                    SegmentedButton(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(freqDisplayName(freq), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Time
            OutlinedButton(onClick = { showTimePicker = true }) {
                Text("%02d:%02d".format(hour, minute))
            }

            // Prompt
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.task_prompt)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // Notification toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.task_notify), modifier = Modifier.weight(1f))
                Switch(checked = notifyEnabled, onCheckedChange = { notifyEnabled = it })
            }

            // Save
            Button(
                onClick = {
                    if (title.isBlank() || prompt.isBlank()) return@Button
                    onSave(
                        ScheduledTask(
                            id = task?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            frequency = frequency,
                            hour = hour,
                            minute = minute,
                            prompt = prompt.trim(),
                            notifyEnabled = notifyEnabled,
                            enabled = true,
                            createdAt = task?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && prompt.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { h, m -> hour = h; minute = m; showTimePicker = false },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun freqDisplayName(freq: TaskFrequency): String = when (freq) {
    TaskFrequency.DAILY -> stringResource(R.string.freq_daily)
    TaskFrequency.WEEKDAYS -> stringResource(R.string.freq_weekdays)
    TaskFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
    TaskFrequency.ONCE -> stringResource(R.string.freq_once)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = { TimePicker(state = state) }
    )
}
