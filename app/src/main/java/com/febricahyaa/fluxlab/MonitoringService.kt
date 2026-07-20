package com.febricahyaa.fluxlab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MonitoringService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fluxlab)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.monitoring_notification))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "fluxlab_measurement"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_START = "com.febricahyaa.fluxlab.START_MONITORING"
        private const val ACTION_STOP = "com.febricahyaa.fluxlab.STOP_MONITORING"

        fun setRunning(context: Context, running: Boolean) {
            val intent = Intent(context, MonitoringService::class.java).setAction(if (running) ACTION_START else ACTION_STOP)
            if (running) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }
    }
}
