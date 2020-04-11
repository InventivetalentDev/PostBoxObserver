package org.inventivetalent.postboxapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.widget.Toast

class TheBroadcastReceiver:BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if(context!=null) {
            if (intent != null) {
                if ("android.intent.action.BOOT_COMPLETED" == intent.action) {
                    val serviceIntent = Intent(context, TheService::class.java)
                    context.startService(serviceIntent)
                } else {
                    Toast.makeText(
                        context.applicationContext,
                        "Alarm Manager just ran",
                        Toast.LENGTH_LONG
                    ).show()
                    println("Alarm Manager just ran")
                }
            }


            SensorBackgroundService.start(context.applicationContext, Sensor.TYPE_PROXIMITY)
        }
    }

}