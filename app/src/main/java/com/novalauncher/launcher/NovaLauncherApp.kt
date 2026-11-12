package com.novalauncher.launcher

import android.app.Application
import com.novalauncher.launcher.utils.AppPreferences

/**
 * Application entry point for NovaLauncher.
 * Initializes persistent preferences and global dependencies.
 */
class NovaLauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
    }
}

