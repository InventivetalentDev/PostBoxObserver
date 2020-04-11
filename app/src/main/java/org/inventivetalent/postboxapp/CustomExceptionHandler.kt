package org.inventivetalent.postboxapp

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

class CustomExceptionHandler : Thread.UncaughtExceptionHandler {

    private val activity: Activity

    constructor(activity: Activity){
        this.activity = activity
    }
    
    override fun uncaughtException(t: Thread, e: Throwable) {
        val intent = Intent(activity, MainActivity::class.java)
        intent.putExtra("crash", true)
        intent.putExtra("crashTime", System.currentTimeMillis())
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(PostBoxApp.instance?.baseContext, 0, intent,PendingIntent.FLAG_ONE_SHOT)

        val alarmManager = PostBoxApp.instance?.baseContext?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+100, pendingIntent)

        activity.finish()
        exitProcess(2)
    }
}