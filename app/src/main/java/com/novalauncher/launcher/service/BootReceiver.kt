package com.novalauncher.launcher.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.novalauncher.launcher.LauncherActivity
import com.novalauncher.launcher.utils.AppPreferences

/**
 * BroadcastReceiver triggered on system boot (BOOT_COMPLETED & LOCKED_BOOT_COMPLETED).
 *
 * Responsibilities:
 * - Warms up launcher state and launches LauncherActivity as home.
 * - Restarts any background or foreground services (such as SystemUIOverlayService) if enabled in preferences.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val OVERLAY_SERVICE_CLASS = "com.novalauncher.launcher.service.SystemUIOverlayService"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "onReceive action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            AppPreferences.init(context)

            // 1. Launch Main Home Activity
            try {
                val launchIntent = Intent(context, LauncherActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    addCategory(Intent.CATEGORY_HOME)
                }
                context.startActivity(launchIntent)
                Log.i(TAG, "Successfully started LauncherActivity from BootReceiver")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LauncherActivity on boot: ${e.message}", e)
            }

            // 2. Restart System UI Overlay Service if configured
            if (AppPreferences.isQuickSettingsEnabled(context) || AppPreferences.isNotificationShadeEnabled(context)) {
                try {
                    val serviceIntent = Intent().apply {
                        component = ComponentName(context, OVERLAY_SERVICE_CLASS)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.i(TAG, "Requested start of SystemUIOverlayService")
                } catch (e: Exception) {
                    Log.w(TAG, "SystemUIOverlayService could not be started: ${e.message}")
                }
            }
        }
    }
}

