package org.inventivetalent.postboxapp

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryInfo {

    val isCharging: Boolean
    val batteryPct: Float?

    init {
        val batteryStatus: Intent? =
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                MainActivity.instance?.applicationContext?.registerReceiver(
                    null,
                    ifilter
                )
            }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
        batteryPct = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
    }

}