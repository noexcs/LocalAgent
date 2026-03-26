package com.noexcs.localagent.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskScheduler.EXTRA_TASK_ID) ?: return

        val workData = Data.Builder()
            .putString(TaskExecutionWorker.KEY_TASK_ID, taskId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TaskExecutionWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
