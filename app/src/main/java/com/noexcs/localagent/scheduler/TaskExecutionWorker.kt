package com.noexcs.localagent.scheduler

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.noexcs.localagent.R
import com.noexcs.localagent.api.ChatMessage
import com.noexcs.localagent.api.OpenAiClient
import com.noexcs.localagent.data.ScheduledTaskRepository
import com.noexcs.localagent.data.SettingsManager
import com.noexcs.localagent.data.TaskFrequency

class TaskExecutionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        // Ensure the notification channel exists
        TaskNotificationHelper(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.task_running))
            .setSilent(true)
            .build()
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val repo = ScheduledTaskRepository(applicationContext)
        val task = repo.load(taskId) ?: return Result.failure()

        if (!task.enabled) return Result.success()

        // Run as foreground so the network call isn't killed
        try { setForeground(getForegroundInfo()) } catch (_: Exception) {}

        val settings = SettingsManager(applicationContext)
        val client = OpenAiClient(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model
        )

        return try {
            val messages = listOf(
                ChatMessage(role = "user", content = task.prompt)
            )
            val response = client.chat(messages)
            val reply = response.choices.firstOrNull()?.message?.content ?: ""

            if (task.notifyEnabled) {
                TaskNotificationHelper(applicationContext).notify(task.title, reply)
            }

            // For ONCE tasks, disable after execution
            if (task.frequency == TaskFrequency.ONCE) {
                repo.save(task.copy(enabled = false))
            } else {
                // Reschedule for next occurrence
                TaskScheduler(applicationContext).schedule(task)
            }

            Result.success()
        } catch (e: Exception) {
            if (task.notifyEnabled) {
                TaskNotificationHelper(applicationContext)
                    .notify(task.title, "Error: ${e.message}")
            }
            Result.failure()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "scheduled_tasks"
        private const val FOREGROUND_NOTIFICATION_ID = 9999
    }
}
