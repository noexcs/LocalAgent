package com.noexcs.localagent.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.noexcs.localagent.data.task.ScheduledTask
import com.noexcs.localagent.data.task.ScheduledTaskRepository
import com.noexcs.localagent.data.task.TaskFrequency
import java.util.Calendar

class TaskScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Returns true if exact alarms are permitted. */
    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    /**
     * Schedule the task. Returns false if the exact-alarm permission is missing.
     */
    fun schedule(task: ScheduledTask): Boolean {
        if (!task.enabled) return true
        if (!canScheduleExact()) return false
        val triggerTime = nextTriggerTime(task)
        val pendingIntent = buildPendingIntent(task.id)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
        return true
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(buildPendingIntent(taskId))
    }

    fun rescheduleAll() {
        val repo = ScheduledTaskRepository(context)
        repo.listAll().filter { it.enabled }.forEach { schedule(it) }
    }

    private fun buildPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_TASK_ALARM
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerTime(task: ScheduledTask): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time already passed today, move to next valid day
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        // For WEEKDAYS, skip weekends
        if (task.frequency == TaskFrequency.WEEKDAYS) {
            while (target.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                target.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            ) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // For WEEKLY, find the next occurrence of the same weekday as creation
        if (task.frequency == TaskFrequency.WEEKLY) {
            val createdDay = Calendar.getInstance().apply {
                timeInMillis = task.createdAt
            }.get(Calendar.DAY_OF_WEEK)
            while (target.get(Calendar.DAY_OF_WEEK) != createdDay) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return target.timeInMillis
    }

    companion object {
        const val ACTION_TASK_ALARM = "com.noexcs.localagent.ACTION_TASK_ALARM"
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
