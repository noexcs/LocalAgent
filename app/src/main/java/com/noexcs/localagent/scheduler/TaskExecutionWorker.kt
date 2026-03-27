package com.noexcs.localagent.scheduler

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.noexcs.localagent.R
import com.noexcs.localagent.data.task.ScheduledTaskRepository
import com.noexcs.localagent.data.task.TaskFrequency

class TaskExecutionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
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

        try { setForeground(getForegroundInfo()) } catch (_: Exception) {}

        return try {
            // TODO: Integrate with Koog Agent for scheduled task execution
            val reply = "Task executed: ${task.prompt}"

            if (task.notifyEnabled) {
                TaskNotificationHelper(applicationContext).notify(task.title, reply)
            }

            if (task.frequency == TaskFrequency.ONCE) {
                repo.save(task.copy(enabled = false))
            } else {
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
