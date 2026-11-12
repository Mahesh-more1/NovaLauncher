package com.novalauncher.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import java.lang.ref.WeakReference

/**
 * WidgetManager — Adaptive AppWidget host for NovaLauncher.
 *
 * Responsibilities:
 *  • Allocates widget IDs via AppWidgetHost.
 *  • Inflates AppWidgetHostViews into caller-supplied FrameLayout containers.
 *  • Supports add / remove / resize operations.
 *  • Wraps each host view in a lightweight SurfaceView for zero-overhead
 *    off-screen recycling (the Surface is released when invisible).
 *  • Listens for ACTION_APPWIDGET_UPDATE broadcasts to trigger efficient
 *    partial updates instead of full re-inflation.
 *  • Memory-safe: releases Surface + host view when widget scrolls off-screen.
 *
 * Usage:
 *   val wm = WidgetManager(context)
 *   wm.startListening()                   // must call in onResume / onStart
 *   val id = wm.allocateWidgetId()
 *   wm.addWidget(id, providerInfo, container, widthDp, heightDp)
 *   wm.removeWidget(id)
 *   wm.stopListening()                    // call in onPause / onStop
 *   wm.destroy()                          // call in onDestroy
 */
class WidgetManager(private val context: Context) {

    companion object {
        private const val TAG = "WidgetManager"
        private const val HOST_ID = 0x4E6F7661   // "Nova" in hex
        private const val PREFS_NAME = "nova_widgets"
        private const val PREFS_KEY_IDS = "widget_ids"
    }

    // -------------------------------------------------------------------------
    // Core Android widget infrastructure
    // -------------------------------------------------------------------------

    private val appWidgetManager: AppWidgetManager =
        AppWidgetManager.getInstance(context.applicationContext)

    private val appWidgetHost: AppWidgetHost =
        AppWidgetHost(context.applicationContext, HOST_ID)

    // -------------------------------------------------------------------------
    // Live widget registry: widgetId → WidgetRecord
    // -------------------------------------------------------------------------

    private val registry = mutableMapOf<Int, WidgetRecord>()

    // -------------------------------------------------------------------------
    // Broadcast receiver for update events
    // -------------------------------------------------------------------------

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                    val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ids?.forEach { id ->
                        registry[id]?.let { record ->
                            if (record.hostView?.isVisible == true) {
                                record.hostView?.updateAppWidgetSize(
                                    null,
                                    record.widthDp,
                                    record.heightDp,
                                    record.widthDp,
                                    record.heightDp
                                )
                            }
                        }
                    }
                }
                AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    registry[id]?.hostView?.invalidate()
                }
            }
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Call in Activity/Fragment onStart or onResume */
    fun startListening() {
        appWidgetHost.startListening()
        val filter = IntentFilter().apply {
            addAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            addAction(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)
        }
        context.registerReceiver(updateReceiver, filter)
        Log.d(TAG, "startListening: host active")
    }

    /** Call in Activity/Fragment onStop or onPause */
    fun stopListening() {
        try { context.unregisterReceiver(updateReceiver) } catch (_: IllegalArgumentException) {}
        appWidgetHost.stopListening()
        Log.d(TAG, "stopListening: host paused")
    }

    /** Call in Activity/Fragment onDestroy */
    fun destroy() {
        stopListening()
        registry.values.forEach { it.release() }
        registry.clear()
        appWidgetHost.deleteHost()
        Log.d(TAG, "destroy: host deleted")
    }

    // =========================================================================
    // Widget lifecycle API
    // =========================================================================

    /** Allocate a new widget ID from the host (use before binding). */
    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    /**
     * Bind and inflate a widget into [container].
     *
     * @param widgetId     ID allocated via [allocateWidgetId]
     * @param providerInfo Info from AppWidgetManager.getInstalledProviders()
     * @param container    FrameLayout that will hold the widget view
     * @param widthDp      Desired width in dp
     * @param heightDp     Desired height in dp
     */
    fun addWidget(
        widgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        container: FrameLayout,
        widthDp: Int,
        heightDp: Int
    ) {
        if (registry.containsKey(widgetId)) {
            Log.w(TAG, "addWidget: $widgetId already managed")
            return
        }

        // Bind if not already bound
        if (!appWidgetManager.getAppWidgetIds(providerInfo.provider).contains(widgetId)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, providerInfo.provider)
            }
        }

        // Create the host view
        val hostView = appWidgetHost.createView(
            context.applicationContext,
            widgetId,
            providerInfo
        ) as AppWidgetHostView

        // Set sizing hints so the provider renders at the right size
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        hostView.updateAppWidgetOptions(options)
        hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)

        // Wrap in a SurfaceView-backed container for zero-overhead rendering
        val surfaceWrapper = WidgetSurfaceWrapper(context, hostView)
        surfaceWrapper.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        container.removeAllViews()
        container.addView(surfaceWrapper)

        val record = WidgetRecord(
            widgetId = widgetId,
            providerInfo = providerInfo,
            container = WeakReference(container),
            surfaceWrapper = WeakReference(surfaceWrapper),
            hostView = hostView,
            widthDp = widthDp,
            heightDp = heightDp
        )
        registry[widgetId] = record

        persistIds()
        Log.d(TAG, "addWidget: $widgetId (${providerInfo.provider.className}) @ ${widthDp}x${heightDp}dp")
    }

    /**
     * Remove a widget, releasing its resources.
     */
    fun removeWidget(widgetId: Int) {
        val record = registry.remove(widgetId) ?: return
        record.release()
        appWidgetHost.deleteAppWidgetId(widgetId)
        persistIds()
        Log.d(TAG, "removeWidget: $widgetId removed")
    }

    /**
     * Resize an existing widget.
     */
    fun resizeWidget(widgetId: Int, newWidthDp: Int, newHeightDp: Int) {
        val record = registry[widgetId] ?: return
        record.widthDp = newWidthDp
        record.heightDp = newHeightDp

        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, newWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, newHeightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, newWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, newHeightDp)
        }
        record.hostView?.updateAppWidgetOptions(options)
        record.hostView?.updateAppWidgetSize(null, newWidthDp, newHeightDp, newWidthDp, newHeightDp)

        // Resize container
        record.container.get()?.let { container ->
            val lp = container.layoutParams
            val density = context.resources.displayMetrics.density
            lp?.width = (newWidthDp * density).toInt()
            lp?.height = (newHeightDp * density).toInt()
            container.layoutParams = lp
        }
        Log.d(TAG, "resizeWidget: $widgetId → ${newWidthDp}x${newHeightDp}dp")
    }

    /**
     * Called when a widget scrolls off-screen — releases the Surface to save memory.
     */
    fun onWidgetOffScreen(widgetId: Int) {
        registry[widgetId]?.surfaceWrapper?.get()?.releaseSurface()
        Log.d(TAG, "onWidgetOffScreen: $widgetId surface released")
    }

    /**
     * Called when a widget scrolls back on-screen — re-creates the Surface.
     */
    fun onWidgetOnScreen(widgetId: Int) {
        registry[widgetId]?.surfaceWrapper?.get()?.reacquireSurface()
        Log.d(TAG, "onWidgetOnScreen: $widgetId surface acquired")
    }

    // =========================================================================
    // Widget category helpers
    // =========================================================================

    enum class WidgetCategory {
        CLOCK, WEATHER, MUSIC, CALENDAR, BATTERY, STEPS, OTHER
    }

    fun getInstalledWidgetsByCategory(): Map<WidgetCategory, List<AppWidgetProviderInfo>> {
        val all = appWidgetManager.installedProviders
        val map = mutableMapOf<WidgetCategory, MutableList<AppWidgetProviderInfo>>()
        WidgetCategory.values().forEach { map[it] = mutableListOf() }

        all.forEach { info ->
            val category = classifyWidget(info)
            map[category]!!.add(info)
        }
        return map
    }

    private fun classifyWidget(info: AppWidgetProviderInfo): WidgetCategory {
        val label = info.loadLabel(context.packageManager).lowercase()
        return when {
            "clock" in label || "time" in label         -> WidgetCategory.CLOCK
            "weather" in label || "forecast" in label   -> WidgetCategory.WEATHER
            "music" in label || "player" in label
                    || "spotify" in label || "media" in label -> WidgetCategory.MUSIC
            "calendar" in label || "agenda" in label    -> WidgetCategory.CALENDAR
            "battery" in label || "charge" in label     -> WidgetCategory.BATTERY
            "step" in label || "fitness" in label
                    || "health" in label                -> WidgetCategory.STEPS
            else                                        -> WidgetCategory.OTHER
        }
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    private fun persistIds() {
        val ids = registry.keys.joinToString(",")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY_IDS, ids).apply()
    }

    fun loadPersistedIds(): List<Int> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY_IDS, "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    // =========================================================================
    // Internal data
    // =========================================================================

    private inner class WidgetRecord(
        val widgetId: Int,
        val providerInfo: AppWidgetProviderInfo,
        val container: WeakReference<FrameLayout>,
        val surfaceWrapper: WeakReference<WidgetSurfaceWrapper>,
        var hostView: AppWidgetHostView?,
        var widthDp: Int,
        var heightDp: Int
    ) {
        fun release() {
            surfaceWrapper.get()?.releaseSurface()
            container.get()?.removeAllViews()
            hostView = null
        }
    }
}

// =============================================================================
// WidgetSurfaceWrapper
//
// Wraps an AppWidgetHostView inside a SurfaceView-backed FrameLayout.
// When releaseSurface() is called (widget off-screen), the Surface is
// destroyed and the hostView detached — freeing GPU memory.
// When reacquireSurface() is called the Surface is re-created and the
// hostView re-attached.
// =============================================================================

class WidgetSurfaceWrapper(
    context: Context,
    private val hostView: AppWidgetHostView
) : FrameLayout(context), SurfaceHolder.Callback {

    private val surfaceView = SurfaceView(context)
    private var surfaceReady = false

    init {
        // SurfaceView sits behind — the AppWidgetHostView renders on top
        surfaceView.setZOrderOnTop(false)
        surfaceView.holder.addCallback(this)
        surfaceView.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        addView(
            surfaceView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    // -------------------------------------------------------------------------
    // SurfaceHolder.Callback
    // -------------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        attachHostView()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        hostView.invalidate()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        detachHostView()
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun releaseSurface() {
        if (!surfaceReady) return
        detachHostView()
        surfaceView.visibility = View.INVISIBLE
    }

    fun reacquireSurface() {
        surfaceView.visibility = View.VISIBLE
        if (surfaceReady) attachHostView()
        // If not ready, surfaceCreated callback will attach
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun attachHostView() {
        if (hostView.parent == null) {
            addView(
                hostView,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun detachHostView() {
        if (hostView.parent == this) removeView(hostView)
    }
}
