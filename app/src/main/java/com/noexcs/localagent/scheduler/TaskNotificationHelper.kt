package com.noexcs.localagent.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.noexcs.localagent.R

class TaskNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_tasks),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_tasks_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun notify(title: String, body: String) {
        val summary = if (body.length > 200) body.take(200) + "…" else body
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(title.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "scheduled_tasks"
    }
}
