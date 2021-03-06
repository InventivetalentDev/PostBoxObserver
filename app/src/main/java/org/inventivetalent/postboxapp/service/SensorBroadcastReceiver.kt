package org.inventivetalent.postboxapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.widget.Toast

class SensorBroadcastReceiver:BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if(context!=null) {
            if (intent != null) {
                if ("android.intent.action.BOOT_COMPLETED" == intent.action) {
                    val serviceIntent = Intent(context, SensorService::class.java)
                    context.startService(serviceIntent)
                } else {
                    Toast.makeText(
                        context.applicationContext,
                        "Sensor Alarm Manager just ran",
                        Toast.LENGTH_LONG
                    ).show()
                    println("Sensor Alarm Manager just ran")
                }
            }


            SensorBackgroundService.start(
                context.applicationContext,
                Sensor.TYPE_PROXIMITY
            )
        }
    }

}