package org.inventivetalent.postboxapp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

	public static final String DEFAULT_CHANNEL_ID = "postbox_default_notifications";

	public NotificationHelper() {
	}

	public static void createNotificationChannel(Context context) {
		// Create the NotificationChannel, but only on API 26+ because
		// the NotificationChannel class is new and not in the support library
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			CharSequence name = "PostBox";
			String description = "PostBox";
			int importance = NotificationManager.IMPORTANCE_DEFAULT;
			NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL_ID, name, importance);
			channel.setDescription(description);
			// Register the channel with the system; you can't change the importance
			// or other notification behaviors after this
			NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
			}
		}
	}

	public static NotificationCompat.Builder newBuilder(Context context, String channel) {
		return new NotificationCompat.Builder(context, channel)
				.setSmallIcon(R.drawable.ic_launcher_foreground)
				.setAutoCancel(true);
	}

	public static void sendNotification(Context context, Notification notification, int id) {
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		assert notificationManager != null;
		notificationManager.notify(id, notification);
	}


	public static void cancelNotification(Context context, int id) {
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		assert notificationManager != null;
		notificationManager.cancel(id);
	}

	public static void cancelScheduledNotification(Context context, PendingIntent pendingIntent) {
		if (pendingIntent == null) { return; }
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		assert alarmManager != null;
		alarmManager.cancel(pendingIntent);
	}

	public static void cancelExistingOrScheduledNotification(Context context, PendingIntent pendingIntent, int id) {
		cancelScheduledNotification(context, pendingIntent);
		cancelNotification(context, id);
	}

}
