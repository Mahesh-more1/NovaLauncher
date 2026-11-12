package com.novalauncher.launcher.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Thread-safe SharedPreferences wrapper singleton for NovaLauncher settings.
 *
 * Handles persistence for:
 * - Home screen grid configuration (columns, icon size, label visibility)
 * - Anti-gravity & fluid dynamics physics parameters
 * - Appearance (dock style, icon packs, wallpaper URI)
 * - Quick Settings layout and tile ordering
 * - System UI overlay toggles and performance options
 */
object AppPreferences {

    private const val PREFS_NAME = "nova_launcher_prefs"

    // Preference Keys
    private const val KEY_GRID_COLUMNS = "pref_grid_columns"
    private const val KEY_ICON_SIZE = "pref_icon_size"
    private const val KEY_SHOW_ICON_LABELS = "pref_show_icon_labels"
    private const val KEY_DOCK_APPS = "pref_dock_apps"
    private const val KEY_DOCK_STYLE = "pref_dock_style"
    private const val KEY_PAGE_COUNT = "pref_page_count"
    private const val KEY_WALLPAPER_URI = "pref_wallpaper_uri"
    private const val KEY_PHYSICS_ENABLED = "pref_physics_enabled"
    private const val KEY_ANTI_GRAVITY_STRENGTH = "pref_anti_gravity_strength"
    private const val KEY_GRAVITY_STRENGTH = "pref_gravity_strength"
    private const val KEY_FLUID_RESISTANCE = "pref_fluid_resistance"
    private const val KEY_COLLISION_DETECTION = "pref_collision_detection"
    private const val KEY_WIDGET_POSITIONS = "pref_widget_positions"
    private const val KEY_SELECTED_ICON_PACK = "pref_selected_icon_pack"
    private const val KEY_QS_TILE_ORDER = "pref_qs_tile_order"
    private const val KEY_QUICK_SETTINGS_ENABLED = "pref_qs_enabled"
    private const val KEY_NOTIFICATION_SHADE_ENABLED = "pref_notification_shade_enabled"
    private const val KEY_SMOOTH_ANIMATIONS = "pref_smooth_animations"
    private const val KEY_BATTERY_SAVER_MODE = "pref_battery_saver_mode"

    // Defaults
    const val DEFAULT_GRID_COLUMNS = 4
    const val DEFAULT_ICON_SIZE = 56
    const val DEFAULT_SHOW_ICON_LABELS = true
    const val DEFAULT_PAGE_COUNT = 1
    const val DEFAULT_PHYSICS_ENABLED = true
    const val DEFAULT_ANTI_GRAVITY_STRENGTH = 1.0f
    const val DEFAULT_GRAVITY_STRENGTH = 1.0f
    const val DEFAULT_FLUID_RESISTANCE = 0.02f
    const val DEFAULT_COLLISION_DETECTION = true
    const val DEFAULT_WIDGET_POSITIONS_JSON = "[]"
    const val DEFAULT_DOCK_STYLE = "frosted"
    const val DEFAULT_QUICK_SETTINGS_ENABLED = true
    const val DEFAULT_NOTIFICATION_SHADE_ENABLED = true
    const val DEFAULT_SMOOTH_ANIMATIONS = true
    const val DEFAULT_BATTERY_SAVER_MODE = false

    val DEFAULT_DOCK_APPS = listOf(
        "com.google.android.dialer",
        "com.google.android.apps.messaging",
        "com.android.chrome",
        "com.google.android.apps.photos"
    )

    val DEFAULT_QS_TILE_ORDER = listOf(
        "wifi",
        "bluetooth",
        "airplane",
        "flashlight",
        "rotation",
        "dnd",
        "battery",
        "settings"
    )

    @Volatile
    private var sharedPreferences: SharedPreferences? = null

    /**
     * Initializes AppPreferences with application context.
     * Safe to call multiple times.
     */
    fun init(context: Context) {
        if (sharedPreferences == null) {
            synchronized(this) {
                if (sharedPreferences == null) {
                    sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun getPrefs(context: Context? = null): SharedPreferences {
        sharedPreferences?.let { return it }
        if (context != null) {
            init(context)
            return sharedPreferences!!
        }
        throw IllegalStateException("AppPreferences must be initialized via init(context) before use")
    }

    // ── Grid Columns ─────────────────────────────────────────────────────────

    var gridColumns: Int
        get() = sharedPreferences?.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS) ?: DEFAULT_GRID_COLUMNS
        set(value) {
            sharedPreferences?.edit()?.putInt(KEY_GRID_COLUMNS, value.coerceIn(2, 7))?.apply()
        }

    fun getGridColumns(context: Context): Int =
        getPrefs(context).getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)

    fun setGridColumns(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(2, 7)).apply()
    }

    // ── Icon Size ────────────────────────────────────────────────────────────

    var iconSize: Int
        get() = sharedPreferences?.getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE) ?: DEFAULT_ICON_SIZE
        set(value) {
            sharedPreferences?.edit()?.putInt(KEY_ICON_SIZE, value.coerceIn(32, 96))?.apply()
        }

    fun getIconSize(context: Context): Int =
        getPrefs(context).getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE)

    fun setIconSize(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_ICON_SIZE, value.coerceIn(32, 96)).apply()
    }

    // ── Show Icon Labels ─────────────────────────────────────────────────────

    var showIconLabels: Boolean
        get() = sharedPreferences?.getBoolean(KEY_SHOW_ICON_LABELS, DEFAULT_SHOW_ICON_LABELS) ?: DEFAULT_SHOW_ICON_LABELS
        set(value) {
            sharedPreferences?.edit()?.putBoolean(KEY_SHOW_ICON_LABELS, value)?.apply()
        }

    fun getShowIconLabels(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SHOW_ICON_LABELS, DEFAULT_SHOW_ICON_LABELS)

    fun setShowIconLabels(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ICON_LABELS, value).apply()
    }

    // ── Dock Apps List ───────────────────────────────────────────────────────

    var dockApps: List<String>
        get() {
            val jsonStr = sharedPreferences?.getString(KEY_DOCK_APPS, null) ?: return DEFAULT_DOCK_APPS
            return parseJsonStringList(jsonStr, DEFAULT_DOCK_APPS)
        }
        set(value) {
            val jsonArr = JSONArray(value)
            sharedPreferences?.edit()?.putString(KEY_DOCK_APPS, jsonArr.toString())?.apply()
        }

    fun getDockApps(context: Context): List<String> {
        val jsonStr = getPrefs(context).getString(KEY_DOCK_APPS, null) ?: return DEFAULT_DOCK_APPS
        return parseJsonStringList(jsonStr, DEFAULT_DOCK_APPS)
    }

    fun setDockApps(context: Context, apps: List<String>) {
        val jsonArr = JSONArray(apps)
        getPrefs(context).edit().putString(KEY_DOCK_APPS, jsonArr.toString()).apply()
    }

    // ── Dock Style ───────────────────────────────────────────────────────────

    var dockStyle: String
        get() = sharedPreferences?.getString(KEY_DOCK_STYLE, DEFAULT_DOCK_STYLE) ?: DEFAULT_DOCK_STYLE
        set(value) {
            sharedPreferences?.edit()?.putString(KEY_DOCK_STYLE, value)?.apply()
        }

    fun getDockStyle(context: Context): String =
        getPrefs(context).getString(KEY_DOCK_STYLE, DEFAULT_DOCK_STYLE) ?: DEFAULT_DOCK_STYLE

    fun setDockStyle(context: Context, style: String) {
        getPrefs(context).edit().putString(KEY_DOCK_STYLE, style).apply()
    }

    // ── Page Count ───────────────────────────────────────────────────────────

    var pageCount: Int
        get() = sharedPreferences?.getInt(KEY_PAGE_COUNT, DEFAULT_PAGE_COUNT) ?: DEFAULT_PAGE_COUNT
        set(value) {
            sharedPreferences?.edit()?.putInt(KEY_PAGE_COUNT, value.coerceIn(1, 9))?.apply()
        }

    fun getPageCount(context: Context): Int =
        getPrefs(context).getInt(KEY_PAGE_COUNT, DEFAULT_PAGE_COUNT)

    fun setPageCount(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_PAGE_COUNT, value.coerceIn(1, 9)).apply()
    }

    // ── Wallpaper URI ────────────────────────────────────────────────────────

    var wallpaperUri: String?
        get() = sharedPreferences?.getString(KEY_WALLPAPER_URI, null)
        set(value) {
            sharedPreferences?.edit()?.putString(KEY_WALLPAPER_URI, value)?.apply()
        }

    fun getWallpaperUri(context: Context): String? =
        getPrefs(context).getString(KEY_WALLPAPER_URI, null)

    fun setWallpaperUri(context: Context, uri: String?) {
        getPrefs(context).edit().putString(KEY_WALLPAPER_URI, uri).apply()
    }

    // ── Physics Enabled ──────────────────────────────────────────────────────

    var physicsEnabled: Boolean
        get() = sharedPreferences?.getBoolean(KEY_PHYSICS_ENABLED, DEFAULT_PHYSICS_ENABLED) ?: DEFAULT_PHYSICS_ENABLED
        set(value) {
            sharedPreferences?.edit()?.putBoolean(KEY_PHYSICS_ENABLED, value)?.apply()
        }

    fun isPhysicsEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_PHYSICS_ENABLED, DEFAULT_PHYSICS_ENABLED)

    fun setPhysicsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PHYSICS_ENABLED, enabled).apply()
    }

    // ── Anti-Gravity Strength ────────────────────────────────────────────────

    var antiGravityStrength: Float
        get() = sharedPreferences?.getFloat(KEY_ANTI_GRAVITY_STRENGTH, DEFAULT_ANTI_GRAVITY_STRENGTH) ?: DEFAULT_ANTI_GRAVITY_STRENGTH
        set(value) {
            sharedPreferences?.edit()?.putFloat(KEY_ANTI_GRAVITY_STRENGTH, value.coerceIn(0f, 3f))?.apply()
        }

    fun getAntiGravityStrength(context: Context): Float =
        getPrefs(context).getFloat(KEY_ANTI_GRAVITY_STRENGTH, DEFAULT_ANTI_GRAVITY_STRENGTH)

    fun setAntiGravityStrength(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_ANTI_GRAVITY_STRENGTH, value.coerceIn(0f, 3f)).apply()
    }

    // ── Gravity Strength Slider (-1.0 to 1.0) ────────────────────────────────

    var gravityStrength: Float
        get() = sharedPreferences?.getFloat(KEY_GRAVITY_STRENGTH, DEFAULT_GRAVITY_STRENGTH) ?: DEFAULT_GRAVITY_STRENGTH
        set(value) {
            sharedPreferences?.edit()?.putFloat(KEY_GRAVITY_STRENGTH, value.coerceIn(-1.0f, 1.0f))?.apply()
        }

    fun getGravityStrength(context: Context): Float =
        getPrefs(context).getFloat(KEY_GRAVITY_STRENGTH, DEFAULT_GRAVITY_STRENGTH)

    fun setGravityStrength(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_GRAVITY_STRENGTH, value.coerceIn(-1.0f, 1.0f)).apply()
    }

    // ── Fluid Resistance ─────────────────────────────────────────────────────

    var fluidResistance: Float
        get() = sharedPreferences?.getFloat(KEY_FLUID_RESISTANCE, DEFAULT_FLUID_RESISTANCE) ?: DEFAULT_FLUID_RESISTANCE
        set(value) {
            sharedPreferences?.edit()?.putFloat(KEY_FLUID_RESISTANCE, value.coerceIn(0f, 0.2f))?.apply()
        }

    fun getFluidResistance(context: Context): Float =
        getPrefs(context).getFloat(KEY_FLUID_RESISTANCE, DEFAULT_FLUID_RESISTANCE)

    fun setFluidResistance(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_FLUID_RESISTANCE, value.coerceIn(0f, 0.2f)).apply()
    }

    // ── Collision Detection ──────────────────────────────────────────────────

    var collisionDetectionEnabled: Boolean
        get() = sharedPreferences?.getBoolean(KEY_COLLISION_DETECTION, DEFAULT_COLLISION_DETECTION) ?: DEFAULT_COLLISION_DETECTION
        set(value) {
            sharedPreferences?.edit()?.putBoolean(KEY_COLLISION_DETECTION, value)?.apply()
        }

    fun isCollisionDetectionEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_COLLISION_DETECTION, DEFAULT_COLLISION_DETECTION)

    fun setCollisionDetectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COLLISION_DETECTION, enabled).apply()
    }

    // ── Widget Positions (JSON String) ───────────────────────────────────────

    var widgetPositions: String
        get() = sharedPreferences?.getString(KEY_WIDGET_POSITIONS, DEFAULT_WIDGET_POSITIONS_JSON) ?: DEFAULT_WIDGET_POSITIONS_JSON
        set(value) {
            sharedPreferences?.edit()?.putString(KEY_WIDGET_POSITIONS, value)?.apply()
        }

    fun getWidgetPositions(context: Context): String =
        getPrefs(context).getString(KEY_WIDGET_POSITIONS, DEFAULT_WIDGET_POSITIONS_JSON) ?: DEFAULT_WIDGET_POSITIONS_JSON

    fun setWidgetPositions(context: Context, json: String) {
        getPrefs(context).edit().putString(KEY_WIDGET_POSITIONS, json).apply()
    }

    // ── Selected Icon Pack ───────────────────────────────────────────────────

    var selectedIconPack: String?
        get() = sharedPreferences?.getString(KEY_SELECTED_ICON_PACK, null)
        set(value) {
            sharedPreferences?.edit()?.putString(KEY_SELECTED_ICON_PACK, value)?.apply()
        }

    fun getSelectedIconPack(context: Context): String? =
        getPrefs(context).getString(KEY_SELECTED_ICON_PACK, null)

    fun setSelectedIconPack(context: Context, packageName: String?) {
        getPrefs(context).edit().putString(KEY_SELECTED_ICON_PACK, packageName).apply()
    }

    // ── Quick Settings Tile Order ────────────────────────────────────────────

    var quickSettingsTileOrder: List<String>
        get() {
            val jsonStr = sharedPreferences?.getString(KEY_QS_TILE_ORDER, null) ?: return DEFAULT_QS_TILE_ORDER
            return parseJsonStringList(jsonStr, DEFAULT_QS_TILE_ORDER)
        }
        set(value) {
            val jsonArr = JSONArray(value)
            sharedPreferences?.edit()?.putString(KEY_QS_TILE_ORDER, jsonArr.toString())?.apply()
        }

    fun getQuickSettingsTileOrder(context: Context): List<String> {
        val jsonStr = getPrefs(context).getString(KEY_QS_TILE_ORDER, null) ?: return DEFAULT_QS_TILE_ORDER
        return parseJsonStringList(jsonStr, DEFAULT_QS_TILE_ORDER)
    }

    fun setQuickSettingsTileOrder(context: Context, tiles: List<String>) {
        val jsonArr = JSONArray(tiles)
        getPrefs(context).edit().putString(KEY_QS_TILE_ORDER, jsonArr.toString()).apply()
    }

    // ── Quick Settings & Overlay Toggles ─────────────────────────────────────

    var quickSettingsEnabled: Boolean
        get() = sharedPreferences?.getBoolean(KEY_QUICK_SETTINGS_ENABLED, DEFAULT_QUICK_SETTINGS_ENABLED) ?: DEFAULT_QUICK_SETTINGS_ENABLED
        set(value) {
            sharedPreferences?.edit()?.putBoolean(KEY_QUICK_SETTINGS_ENABLED, value)?.apply()
        }

    fun isQuickSettingsEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_QUICK_SETTINGS_ENABLED, DEFAULT_QUICK_SETTINGS_ENABLED)

    fun setQuickSettingsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_QUICK_SETTINGS_ENABLED, enabled).apply()
    }

    var notificationShadeEnabled: Boolean
        get() = sharedPreferences?.getBoolean(KEY_NOTIFICATION_SHADE_ENABLED, DEFAULT_NOTIFICATION_SHADE_ENABLED) ?: DEFAULT_NOTIFICATION_SHADE_ENABLED
        set(value) {
            sharedPreferences?.edit()?.putBoolean(KEY_NOTIFICATION_SHADE_ENABLED, value)?.apply()
        }

    fun isNotificationShadeEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_NOTIFICATION_SHADE_ENABLED, DEFAULT_NOTIFICATION_SHADE_ENABLED)

    fun setNotificationShadeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATION_SHADE_ENABLED, enabled).apply()
    }

    // ── Performance Options ──────────────────────────────────────────────────

    var smoothAnimations: Boolean
        get() = sharedPreferences?.getBoolean(KEY_SMOOTH_ANIMATIONS, DEFAULT_SMOOTH_ANIMATIONS) ?: DEFAULT_SMOOTH_ANIMATIONS
        set(value) {
            sharedPreferences?.edit()?.putBoolean(KEY_SMOOTH_ANIMATIONS, value)?.apply()
        }

    fun isSmoothAnimations(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SMOOTH_ANIMATIONS, DEFAULT_SMOOTH_ANIMATIONS)

    fun setSmoothAnimations(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SMOOTH_ANIMATIONS, enabled).apply()
    }

    var batterySaverMode: Boolean
        get() = sharedPreferences?.getBoolean(KEY_BATTERY_SAVER_MODE, DEFAULT_BATTERY_SAVER_MODE) ?: DEFAULT_BATTERY_SAVER_MODE
        set(value) {
            sharedPreferences?.edit()?.putBoolean(KEY_BATTERY_SAVER_MODE, value)?.apply()
        }

    fun isBatterySaverMode(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BATTERY_SAVER_MODE, DEFAULT_BATTERY_SAVER_MODE)

    fun setBatterySaverMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BATTERY_SAVER_MODE, enabled).apply()
    }

    // ── Reset to Defaults ────────────────────────────────────────────────────

    /**
     * Resets all launcher configurations back to their factory default values.
     */
    fun resetToDefaults(context: Context? = null) {
        val prefs = if (context != null) getPrefs(context) else sharedPreferences ?: return
        prefs.edit().clear().apply()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun parseJsonStringList(jsonStr: String, fallback: List<String>): List<String> {
        return try {
            val jsonArr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArr.length()) {
                list.add(jsonArr.getString(i))
            }
            list
        } catch (_: Exception) {
            fallback
        }
    }
}

