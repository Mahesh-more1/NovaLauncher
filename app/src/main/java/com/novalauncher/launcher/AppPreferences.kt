package com.novalauncher.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized preferences manager for NovaLauncher.
 * Stores settings for Home Screen, Physics, Appearance, System Overlay, and Performance.
 */
class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "novalauncher_prefs"

        // Home Screen
        const val KEY_GRID_COLUMNS = "pref_grid_columns"
        const val DEFAULT_GRID_COLUMNS = 4

        const val KEY_ICON_SIZE = "pref_icon_size"
        const val DEFAULT_ICON_SIZE = 56 // dp

        const val KEY_SHOW_ICON_LABELS = "pref_show_icon_labels"
        const val DEFAULT_SHOW_ICON_LABELS = true

        // Physics
        const val KEY_PHYSICS_ENABLED = "pref_physics_enabled"
        const val DEFAULT_PHYSICS_ENABLED = true

        const val KEY_GRAVITY_STRENGTH = "pref_gravity_strength"
        const val DEFAULT_GRAVITY_STRENGTH = 1.0f // range -1.0 to 1.0

        const val KEY_FLUID_RESISTANCE = "pref_fluid_resistance"
        const val DEFAULT_FLUID_RESISTANCE = 0.2f // range 0.0 to 1.0

        const val KEY_COLLISION_DETECTION = "pref_collision_detection"
        const val DEFAULT_COLLISION_DETECTION = true

        // Appearance
        const val KEY_ICON_PACK = "pref_icon_pack"
        const val DEFAULT_ICON_PACK = "default"

        const val KEY_DOCK_STYLE = "pref_dock_style"
        const val DOCK_STYLE_FROSTED = "frosted"
        const val DOCK_STYLE_SOLID = "solid"
        const val DOCK_STYLE_TRANSPARENT = "transparent"
        const val DEFAULT_DOCK_STYLE = DOCK_STYLE_FROSTED

        // System Overlay
        const val KEY_ENABLE_QUICK_SETTINGS = "pref_enable_quick_settings"
        const val DEFAULT_ENABLE_QUICK_SETTINGS = true

        const val KEY_ENABLE_NOTIFICATION_SHADE = "pref_enable_notification_shade"
        const val DEFAULT_ENABLE_NOTIFICATION_SHADE = true

        // Performance
        const val KEY_SMOOTH_ANIMATIONS = "pref_smooth_animations"
        const val DEFAULT_SMOOTH_ANIMATIONS = true

        const val KEY_BATTERY_SAVER = "pref_battery_saver"
        const val DEFAULT_BATTERY_SAVER = false

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context).also { instance = it }
            }
        }
    }

    // Home Screen Settings
    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value).apply()

    var iconSize: Int
        get() = prefs.getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE)
        set(value) = prefs.edit().putInt(KEY_ICON_SIZE, value).apply()

    var showIconLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ICON_LABELS, DEFAULT_SHOW_ICON_LABELS)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ICON_LABELS, value).apply()

    // Physics Settings
    var physicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHYSICS_ENABLED, DEFAULT_PHYSICS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_PHYSICS_ENABLED, value).apply()

    var gravityStrength: Float
        get() = prefs.getFloat(KEY_GRAVITY_STRENGTH, DEFAULT_GRAVITY_STRENGTH)
        set(value) = prefs.edit().putFloat(KEY_GRAVITY_STRENGTH, value).apply()

    var fluidResistance: Float
        get() = prefs.getFloat(KEY_FLUID_RESISTANCE, DEFAULT_FLUID_RESISTANCE)
        set(value) = prefs.edit().putFloat(KEY_FLUID_RESISTANCE, value).apply()

    var collisionDetection: Boolean
        get() = prefs.getBoolean(KEY_COLLISION_DETECTION, DEFAULT_COLLISION_DETECTION)
        set(value) = prefs.edit().putBoolean(KEY_COLLISION_DETECTION, value).apply()

    // Appearance Settings
    var iconPack: String
        get() = prefs.getString(KEY_ICON_PACK, DEFAULT_ICON_PACK) ?: DEFAULT_ICON_PACK
        set(value) = prefs.edit().putString(KEY_ICON_PACK, value).apply()

    var dockStyle: String
        get() = prefs.getString(KEY_DOCK_STYLE, DEFAULT_DOCK_STYLE) ?: DEFAULT_DOCK_STYLE
        set(value) = prefs.edit().putString(KEY_DOCK_STYLE, value).apply()

    // System Overlay Settings
    var enableQuickSettings: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_QUICK_SETTINGS, DEFAULT_ENABLE_QUICK_SETTINGS)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_QUICK_SETTINGS, value).apply()

    var enableNotificationShade: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_NOTIFICATION_SHADE, DEFAULT_ENABLE_NOTIFICATION_SHADE)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_NOTIFICATION_SHADE, value).apply()

    // Performance Settings
    var smoothAnimations: Boolean
        get() = prefs.getBoolean(KEY_SMOOTH_ANIMATIONS, DEFAULT_SMOOTH_ANIMATIONS)
        set(value) = prefs.edit().putBoolean(KEY_SMOOTH_ANIMATIONS, value).apply()

    var batterySaver: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_SAVER, DEFAULT_BATTERY_SAVER)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_SAVER, value).apply()

    /**
     * Reset all settings to default values.
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    /**
     * Register a listener for changes to preferences.
     */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Unregister a previously registered preference change listener.
     */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
