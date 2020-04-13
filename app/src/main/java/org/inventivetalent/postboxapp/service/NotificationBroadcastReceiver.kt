package org.inventivetalent.postboxapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationBroadcastReceiver:BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if(context!=null) {
            if (intent != null) {
                if ("android.intent.action.BOOT_COMPLETED" == intent.action) {
                    val serviceIntent = Intent(context, SensorService::class.java)
                    context.startService(serviceIntent)
                } else {
                    println("Notification Alarm Manager just ran")
                }
            }


            NotificationBackgroundService.start(
                context.applicationContext
            )
        }
    }

}