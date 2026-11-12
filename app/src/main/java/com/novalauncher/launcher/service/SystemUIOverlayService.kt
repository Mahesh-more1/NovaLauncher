package com.novalauncher.launcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.novalauncher.launcher.R
import com.novalauncher.launcher.SystemUIOverlay

/**
 * Foreground service that hosts and maintains the SystemUIOverlay instance.
 * Allows the iOS Quick Settings and Notification Shade to operate as a system overlay
 * independently of whether the launcher activity is currently visible.
 */
class SystemUIOverlayService : Service() {

    private var overlay: SystemUIOverlay? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        overlay = SystemUIOverlay(this).apply {
            attach()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_PANEL -> overlay?.showPanel()
            ACTION_HIDE_PANEL -> overlay?.hidePanel()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        overlay?.detach()
        overlay = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_service_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "NovaLauncher System UI overlay active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.quick_settings_label))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "nova_overlay_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW_PANEL = "com.novalauncher.launcher.ACTION_SHOW_PANEL"
        const val ACTION_HIDE_PANEL = "com.novalauncher.launcher.ACTION_HIDE_PANEL"

        fun start(context: Context) {
            val intent = Intent(context, SystemUIOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SystemUIOverlayService::class.java)
            context.stopService(intent)
        }
    }
}
