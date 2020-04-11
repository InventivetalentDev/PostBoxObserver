package org.inventivetalent.postboxapp

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class WebServer(port:Int) : NanoHTTPD(port) {


    override fun serve(session: IHTTPSession?): Response {
        if (session == null) return super.serve(session)
        val uri = session.uri

        if ("/" == uri) {
            return newFixedLengthResponse("Hello World!")
        }
        if ("/info" == uri) {
            val json = JSONObject()
            json.put("time", System.currentTimeMillis())
            val proximity = runBlocking {
               return@runBlocking MainActivity.instance?.dataRepository?.get("proximity")
            }
            val proximityTime = runBlocking {
                return@runBlocking MainActivity.instance?.dataRepository?.get("proximityTime")
            }
            json.put("proximity", proximity?.toFloatOrNull())
            json.put("proximityTime",proximityTime?.toLongOrNull())

            val batteryStatus: Intent? =
                IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    MainActivity.instance?.applicationContext?.registerReceiver(
                        null,
                        ifilter
                    )
                }
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
            val batteryPct: Float? = batteryStatus?.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale.toFloat()
            }
            json.put("charging", isCharging)
            json.put("battery", batteryPct)

            val response =  newFixedLengthResponse(json.toString())
            response.mimeType = "application/json"
            return response
        }

        return super.serve(session)
    }

}