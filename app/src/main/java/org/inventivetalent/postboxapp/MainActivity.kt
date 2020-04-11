package org.inventivetalent.postboxapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity



class MainActivity : AppCompatActivity() {

    companion object {
        var instance: MainActivity? = null

        var currentProximity: Float = -1f
    }

    val APP_PERMISSION_REQUEST = 45487

    var webServer:WebServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Thread.setDefaultUncaughtExceptionHandler(CustomExceptionHandler(this))

        instance = this

        val port = 8090

        webServer = WebServer(port)
        webServer!!.start()

        try {
            val wifiMan = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInf = wifiMan.connectionInfo
            val ipAddress = wifiInf.ipAddress
            val ip = String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
            val msg = "Web Interface running on $ip:$port"
            println(msg)
            Toast.makeText(this,msg,Toast.LENGTH_LONG).show()
        } catch (e:Exception) {

        }

        if (savedInstanceState != null) {
            currentProximity = savedInstanceState.getFloat("proximity")
        }

        val alarmIntent = Intent(this, TheBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, 0)

        val alarmManager = getSystemService(Service.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 0, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, 0, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, 0, pendingIntent);
        }



        if (intent.getBooleanExtra("crash", false)) {
            Toast.makeText(this, "App restarted after crash", Toast.LENGTH_SHORT).show();
//            crashTime = intent.getLongExtra("crashTime",0)/ 1000
        }
        if (intent.getBooleanExtra("kill", false)) {
            Toast.makeText(this, "App restarted after kill", Toast.LENGTH_SHORT).show();
//            killTIme = intent.getLongExtra("killTime", 0)/ 1000
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.run{
            putFloat("proximity", currentProximity)
        }
        super.onSaveInstanceState(outState)
    }


    fun onProximityChanged(prev: Float, curr: Float) {
        println("onProximityChanged($prev->$curr)")
        currentProximity = curr
    }

}
