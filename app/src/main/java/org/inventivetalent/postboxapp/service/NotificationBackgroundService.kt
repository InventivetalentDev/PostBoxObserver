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


        fun start(appContext: Context) {
            println("notification service start")

            val notificationService = Intent(
                appContext, NotificationBackgroundService::class.java
            )
            val args = Bundle()
            notificationService.putExtras(args)
            val scheduledIntent: PendingIntent
            scheduledIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    appContext,
                    0,
                    notificationService,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    appContext,
                    0,
                    notificationService,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                0,
                INTERVAL,
                scheduledIntent
            )
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var date = DateFormat.format("dd-MM-yyyy hh:mm:ss", java.util.Date()).toString()
        println("[$date] notification service start command ${System.currentTimeMillis()} ${hashCode()}")

        if (intent != null) {
            val args = intent.extras
            if (args != null) {
            }
        }

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
        if (info["postBoxFullRecently"] as Boolean) {
            Log.i("NotificationService", "PostBox is newly full!")

            val date = DateFormat.format("dd-MM-yyyy HH:mm:ss", java.util.Date()).toString()
            val battery = info["battery"]
            sendEmails("There's Something in Your PostBox!\n" +
                    "\n" +
                    "PostBox Full Since $date\n" +
                    "Battery Charge at $battery%\n")
        }
    }

    suspend fun sendEmails(text: String) {
        val emailEntries = MainActivity.instance?.emailRepository?.getAll()
        val toMap = HashMap<String, String?>()
        emailEntries?.forEach {
            if(it.address!=null) {
                toMap.put(it.address!!, it.name)
            }
        }
        if (toMap.size > 0) {
            EmailSender.sendEmail(toMap, "You've got Mail!", text)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        println("notification service onBind")
        return null
    }

    override fun onCreate() {
        super.onCreate()

        println("notification service onCreate")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(0, notification)
        }
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