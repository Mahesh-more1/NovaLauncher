package com.novalauncher.launcher

import android.graphics.drawable.Drawable

/**
 * Data class representing an installed application's metadata.
 *
 * @param packageName  The app's package name (e.g. "com.example.app")
 * @param activityName The fully-qualified main activity class name
 * @param label        Human-readable application label
 * @param icon         The app's launcher icon drawable (may be null before loading)
 * @param position     Logical grid position index (0-based, row-major)
 */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?,
    var position: Int = 0
) {
    /**
     * Returns a stable, unique identifier for this app entry used by the
     * RecyclerView DiffUtil and the physics engine object pool.
     */
    val id: String get() = "$packageName/$activityName"
}
