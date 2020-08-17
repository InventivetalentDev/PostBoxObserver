package org.inventivetalent.postboxapp.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.DateFormat
import android.util.Log
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.inventivetalent.postboxapp.EmailSender
import org.inventivetalent.postboxapp.MainActivity
import org.inventivetalent.postboxapp.NotificationHelper
import org.inventivetalent.postboxapp.NotificationHelper.Companion.DEFAULT_CHANNEL_ID
import org.inventivetalent.postboxapp.R
import org.inventivetalent.postboxapp.web.WebServer

class NotificationBackgroundService : Service() {

    private val TAG = NotificationBackgroundService::class.java.simpleName

    var postBoxFull: Boolean = false

    companion object {
        val INTERVAL:Long = 5*60000

        var notification:Notification? =null
        var lastBatteryPct: Float = 0F;

        fun start(appContext: Context) {
            println("notification service start")

            val notificationService = Intent(
                appContext, NotificationBackgroundService::class.java
            )
            val args = Bundle()
            notificationService.putExtras(args)
            val scheduledIntent: PendingIntent
            scheduledIntent =
                PendingIntent.getService(
                    appContext,
                    0,
                    notificationService,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                0,
                INTERVAL,
                scheduledIntent
            )
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val date = WebServer.dateFormat()
        println("[$date] notification service start command ${System.currentTimeMillis()} ${hashCode()}")

        if (intent != null) {
            val args = intent.extras
            if (args != null) {
            }
        }

        MainActivity.instance?.setData("notificationServiceStart", System.currentTimeMillis().toString())

        Log.i("NotificationService", "Checking PostBox...")
        GlobalScope.launch {
            doPostBoxCheck()
        }


        try {
            val builder = NotificationCompat.Builder(applicationContext, DEFAULT_CHANNEL_ID)
                .setContentTitle("PostBox Notification service running")
                .setContentText("Post Box Full: $postBoxFull")
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            notification = builder.build()
            NotificationHelper.sendNotification(
                applicationContext,
                notification!!,
                55
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }


        return START_STICKY
    }

    suspend fun doPostBoxCheck() {
        val info = WebServer.getPostBoxInfo()
        postBoxFull = info["postBoxFull"] as Boolean
        if (!postBoxFull) {
            Log.i("NotificationService", "PostBox is empty")
        }
        val battery = info["battery"] as Float;
        if (info["postBoxFullRecently"] as Boolean) {
            Log.i("NotificationService", "PostBox is newly full!")

            sendEmails(info)
        } else if(battery < 10 && battery < lastBatteryPct) {
            Log.i("NotificationService", "Sending low battery warning email ($lastBatteryPct->$battery)")
            sendEmails(info, R.raw.battery_email, "PostBox battery low!")
        }
        lastBatteryPct = battery
    }

    suspend fun sendEmails(format: Map<String, Any?>) {
        sendEmails(format, R.raw.notification_email)
    }

    suspend fun sendEmails(format: Map<String, Any?>, @RawRes contentFile: Int, subject: String = "You've got Mail!") {
        val emailEntries = MainActivity.instance?.emailRepository?.getAll()
        val toMap = HashMap<String, String?>()
        emailEntries?.forEach {
            if(it.address!=null) {
                toMap.put(it.address!!, it.name)
            }
        }
        if (toMap.size > 0) {
            EmailSender.sendEmail(toMap, subject, contentFile, format)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        println("notification service onBind")
        return null
    }

    override fun onCreate() {
        super.onCreate()

        println("notification service onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()

        println("notification service onDestroy")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        println("notification service onTaskRemoved")
    }
}