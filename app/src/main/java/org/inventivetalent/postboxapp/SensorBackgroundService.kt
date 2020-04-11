package org.inventivetalent.postboxapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.inventivetalent.postboxapp.NotificationHelper.DEFAULT_CHANNEL_ID

class SensorBackgroundService : Service(), SensorEventListener {

    private val TAG = SensorBackgroundService::class.java.simpleName

    private var mSensorManager: SensorManager? = null
    private var mPowerManager: PowerManager? = null
    private var sensorType = -1
    private var previousValue: Float = -1f

    companion object {
        val KEY_SENSOR_TYPE = "sensor_type"

        fun start(appContext: Context, sensorType: Int) {
            println("sensor service start ($sensorType)")

            val sensorService = Intent(
                appContext, SensorBackgroundService::class.java
            )
            val args = Bundle()
            args.putInt(KEY_SENSOR_TYPE, sensorType)
            sensorService.putExtras(args)
            val scheduledIntent: PendingIntent
            scheduledIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    appContext,
                    sensorType + 50,
                    sensorService,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    appContext,
                    sensorType + 50,
                    sensorService,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                0,
                10000L,
                scheduledIntent
            )
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("sensor service start command")

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mPowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        sensorType = Sensor.TYPE_PROXIMITY

        if (intent != null) {
            val args = intent.extras
            if (args != null) {
                if (args.containsKey(KEY_SENSOR_TYPE)) {
                    sensorType = args.getInt(KEY_SENSOR_TYPE)
                }
            }
        }

        val sensor = mSensorManager!!.getDefaultSensor(sensorType)
        mSensorManager!!.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)

        try {
            val builder = NotificationCompat.Builder(applicationContext, DEFAULT_CHANNEL_ID)
                .setContentTitle("PostBox service running")
                .setSmallIcon(R.drawable.ic_mail_outline_black_24dp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            NotificationHelper.sendNotification(applicationContext, builder.build(), 50)
        } catch (e: Exception) {
            e.printStackTrace()
        }


        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        val intent = Intent(MainActivity.instance, MainActivity::class.java)
        intent.putExtra("kill", true)
        intent.putExtra("killTime", System.currentTimeMillis())
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(
            PostBoxApp.instance!!.baseContext,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT
        )

        val alarmManager =
            PostBoxApp.instance!!.baseContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 100,
            pendingIntent
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val sensorValue = event.values[0]

            if (sensorValue != previousValue) {
                val wakeLock = mPowerManager!!.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    TAG
                )
                wakeLock.acquire(5 * 1000L /*10 minutes*/)
                wakeLock.release()

                MainActivity.instance!!.onProximityChanged(previousValue, sensorValue)
                previousValue = sensorValue
            }
        }
    }
}