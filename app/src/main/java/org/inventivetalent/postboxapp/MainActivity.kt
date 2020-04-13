package org.inventivetalent.postboxapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.inventivetalent.postboxapp.database.AppDatabase
import org.inventivetalent.postboxapp.database.entities.Email
import org.inventivetalent.postboxapp.database.repositories.DataRepository
import org.inventivetalent.postboxapp.database.repositories.EmailRepository
import org.inventivetalent.postboxapp.service.NotificationBroadcastReceiver
import org.inventivetalent.postboxapp.service.SensorBroadcastReceiver
import org.inventivetalent.postboxapp.web.WebAuth
import org.inventivetalent.postboxapp.web.WebServer


class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    val TAG = MainActivity::class.simpleName

    companion object {
        var instance: MainActivity? = null

        var currentProximity: Float = -1f
    }

    val APP_PERMISSION_REQUEST = 45487

    var webServer: WebServer? = null

    var dataRepository: DataRepository? = null
    var emailRepository: EmailRepository? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Thread.setDefaultUncaughtExceptionHandler(CustomExceptionHandler(this))

        instance = this

        val appDatabase = AppDatabase.getInstance(this)
        dataRepository = DataRepository(appDatabase.dataDao())
        emailRepository = EmailRepository(appDatabase.emailDao())


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
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val sizeObserver = Observer<Int> { s ->
                run {
                    println("Emails database size changed: $s")
                    if (s == 0) {
                        createAdminAccount()
                    }
                }
            }
            emailRepository?.size?.observe(this, sizeObserver)

            GlobalScope.launch {
                val s = appDatabase.emailDao().getSize()
                println("Emails database size: $s")
                if (s == 0) {
                    createAdminAccount()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (savedInstanceState != null) {
            currentProximity = savedInstanceState.getFloat("proximity")
        }

        launchReceiver(SensorBroadcastReceiver::class.java)
        launchReceiver(NotificationBroadcastReceiver::class.java)


        if (intent.getBooleanExtra("crash", false)) {
            Toast.makeText(this, "App restarted after crash", Toast.LENGTH_SHORT).show();
//            crashTime = intent.getLongExtra("crashTime",0)/ 1000
            setData("crashTime", intent.getLongExtra("crashTime", 0).toString())
        }
        if (intent.getBooleanExtra("kill", false)) {
            Toast.makeText(this, "App restarted after kill", Toast.LENGTH_SHORT).show();
//            killTIme = intent.getLongExtra("killTime", 0)/ 1000
            setData("killTime", intent.getLongExtra("killTime", 0).toString())
        }
    }

    fun <T> launchReceiver(clazz: Class<T>) {
        val alarmIntent = Intent(this, clazz)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, 0)

        val alarmManager = getSystemService(Service.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 0, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, 0, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, 0, pendingIntent)
        }
    }

    fun createAdminAccount() {
        Log.w(TAG, "Emails database is empty. Adding new admin account.")
        val email = Email()
        email.name = "admin"
        email.address = "admin@post.box"
        email.pass = WebAuth.sha512("admin")
        GlobalScope.launch {
            emailRepository?.insert(email)
            Log.i(TAG, "New admin account (admin:admin) created.")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.run {
            putFloat("proximity", currentProximity)
        }
        super.onSaveInstanceState(outState)
    }


    fun onProximityChanged(prev: Float, curr: Float) {
        println("onProximityChanged($prev->$curr)")
        currentProximity = curr

        setData("proximity", curr.toString())
        setData("proximityTime", System.currentTimeMillis().toString())
    }


    fun setData(key: String, value: String) {
        GlobalScope.launch {
            instance?.let { dataRepository?.set(key, value) }
        }
    }

}
