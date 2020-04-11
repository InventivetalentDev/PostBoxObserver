package org.inventivetalent.postboxapp

import android.app.*
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper {
    companion object {

        val DEFAULT_CHANNEL_ID = "postbox_default_notifications"

        fun createNotificationChannel(context: Context) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "PostBox"
                val description = "PostBox"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(DEFAULT_CHANNEL_ID, name, importance)
                channel.description = description
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
        }

        fun newBuilder(context: Context, channel: String): NotificationCompat.Builder {
            return NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
        }

        fun sendNotification(context: Context, notification: Notification, id: Int) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(id, notification)
        }


        fun cancelNotification(context: Context, id: Int) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
        }

        fun cancelScheduledNotification(context: Context, pendingIntent: PendingIntent?) {
            if (pendingIntent == null) {
                return
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }

        fun cancelExistingOrScheduledNotification(
            context: Context,
            pendingIntent: PendingIntent,
            id: Int
        ) {
            cancelScheduledNotification(context, pendingIntent)
            cancelNotification(context, id)
        }
    }

}
