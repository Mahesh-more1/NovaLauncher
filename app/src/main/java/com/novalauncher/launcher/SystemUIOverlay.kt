package com.novalauncher.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

/**
 * NovaLauncher – SystemUIOverlay
 *
 * Renders an iOS-style Quick Settings panel and notification shade using
 * TYPE_APPLICATION_OVERLAY (no root required).  Uses only public Android APIs.
 *
 * Usage:
 *   val overlay = SystemUIOverlay(context)
 *   overlay.attach()          // adds the invisible touch-catcher to WindowManager
 *   overlay.detach()          // removes everything and cleans up
 */
class SystemUIOverlay(private val context: Context) {

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

    var onDismiss: (() -> Unit)? = null

    // -------------------------------------------------------------------------
    // WindowManager + root views
    // -------------------------------------------------------------------------

    private val wm: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Transparent full-screen touch interceptor – always attached when overlay is active */
    private var touchCatcher: View? = null

    /** The actual panel container (slides in from top) */
    private var panelRoot: FrameLayout? = null

    // -------------------------------------------------------------------------
    // Panel sections
    // -------------------------------------------------------------------------

    private var quickSettingsGrid: LinearLayout? = null
    private var notificationList: LinearLayout? = null
    private var clockLabel: TextView? = null
    private var dateLabel: TextView? = null
    private var brightnessSlider: FrostedSeekBar? = null
    private var volumeSlider: FrostedSeekBar? = null

    // -------------------------------------------------------------------------
    // System services
    // -------------------------------------------------------------------------

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // -------------------------------------------------------------------------
    // Bluetooth
    // -------------------------------------------------------------------------

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }

    // -------------------------------------------------------------------------
    // Overlay animation
    // -------------------------------------------------------------------------

    private val screenHeight by lazy {
        wm.currentWindowMetrics.bounds.height()
    }

    private var panelHeight = 0
    private var panelVisible = false
    private var currentAnimator: ValueAnimator? = null

    // -------------------------------------------------------------------------
    // Gesture
    // -------------------------------------------------------------------------

    private var startY = 0f
    private var startX = 0f
    private val SWIPE_DOWN_THRESHOLD = 60f // dp
    private val SWIPE_MIN_VELOCITY = 300f   // px/s (approx)

    // -------------------------------------------------------------------------
    // Live notifications (injected by NovaNotificationListenerService)
    // -------------------------------------------------------------------------

    private val activeNotifications = mutableListOf<NotificationItem>()

    // -------------------------------------------------------------------------
    // Clock updater
    // -------------------------------------------------------------------------

    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 30_000)
        }
    }

    // -------------------------------------------------------------------------
    // Vibrator
    // -------------------------------------------------------------------------

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // =========================================================================
    // Attach / Detach
    // =========================================================================

    fun attach() {
        if (touchCatcher != null) return
        buildTouchCatcher()
        buildPanel()
        handler.post(clockRunnable)
    }

    fun detach() {
        handler.removeCallbacks(clockRunnable)
        currentAnimator?.cancel()
        touchCatcher?.let { wm.removeView(it) }
        panelRoot?.let { wm.removeView(it) }
        touchCatcher = null
        panelRoot = null
        panelVisible = false
    }

    fun pushNotification(item: NotificationItem) {
        activeNotifications.removeAll { it.key == item.key }
        activeNotifications.add(0, item)
        if (panelVisible) refreshNotificationList()
    }

    fun removeNotification(key: String) {
        activeNotifications.removeAll { it.key == key }
        if (panelVisible) refreshNotificationList()
    }

    // =========================================================================
    // WindowManager helpers
    // =========================================================================

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun buildWindowParams(
        w: Int = WindowManager.LayoutParams.MATCH_PARENT,
        h: Int = WindowManager.LayoutParams.MATCH_PARENT,
        flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    ) = WindowManager.LayoutParams(
        w, h, overlayType(), flags, PixelFormat.TRANSLUCENT
    ).also { p ->
        p.gravity = Gravity.TOP or Gravity.START
    }

    // =========================================================================
    // Touch catcher – intercepts swipe-down from top edge
    // =========================================================================

    private fun buildTouchCatcher() {
        val params = buildWindowParams(
            h = dpToPx(24), // just top 24 dp for edge swipe
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        params.gravity = Gravity.TOP or Gravity.START

        val catcher = object : View(context) {
            private var downY = 0f

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - downY
                        if (dy > dpToPx(SWIPE_DOWN_THRESHOLD) && !panelVisible) {
                            showPanel()
                            return true
                        }
                    }
                }
                return false
            }
        }

        touchCatcher = catcher
        wm.addView(catcher, params)
    }

    // =========================================================================
    // Panel construction
    // =========================================================================

    private fun buildPanel() {
        val density = context.resources.displayMetrics.density

        // ---- Root FrameLayout ------------------------------------------------
        val root = FrameLayout(context)
        root.alpha = 0f

        val rootParams = buildWindowParams(
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        panelRoot = root
        wm.addView(root, rootParams)

        // ---- Frosted glass background ----------------------------------------
        val backdrop = FrostedGlassView(context)
        val backdropLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(backdrop, backdropLp)

        // Dismiss on tap outside panel
        backdrop.setOnClickListener { hidePanel() }

        // ---- Slide-in content panel -------------------------------------------
        val panel = LinearLayout(context)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dpToPx(16), dpToPx(52), dpToPx(16), dpToPx(16))
        panel.setBackgroundColor(Color.TRANSPARENT)

        val panelLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (screenHeight * 0.70).toInt()
        )
        panelLp.gravity = Gravity.TOP
        root.addView(panel, panelLp)
        panelHeight = (screenHeight * 0.70).toInt()

        // ---- Clock -----------------------------------------------------------
        val clockRow = LinearLayout(context)
        clockRow.orientation = LinearLayout.VERTICAL
        clockRow.gravity = Gravity.CENTER_HORIZONTAL

        clockLabel = TextView(context).apply {
            textSize = 60f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        dateLabel = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.argb(200, 255, 255, 255))
            gravity = Gravity.CENTER
        }
        updateClock()
        clockRow.addView(clockLabel)
        clockRow.addView(dateLabel)
        panel.addView(clockRow, lp(MATCH, WRAP))

        addSpacer(panel, 24)

        // ---- Quick Settings Grid ---------------------------------------------
        quickSettingsGrid = buildQuickSettingsGrid()
        panel.addView(quickSettingsGrid, lp(MATCH, WRAP))

        addSpacer(panel, 20)

        // ---- Brightness slider -----------------------------------------------
        addSliderRow(panel, "Brightness", ContextCompat.getDrawable(context, android.R.drawable.ic_menu_camera)) { value ->
            val brightness = (value * 255).toInt()
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness.coerceIn(10, 255)
                )
            } catch (_: SecurityException) {}
        }

        addSpacer(panel, 12)

        // ---- Volume slider ---------------------------------------------------
        addSliderRow(panel, "Volume", ContextCompat.getDrawable(context, android.R.drawable.ic_lock_silent_mode_off)) { value ->
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (value * maxVol).toInt(),
                0
            )
        }

        addSpacer(panel, 24)

        // ---- Notification list -----------------------------------------------
        val notifTitle = TextView(context).apply {
            text = "Notifications"
            textSize = 13f
            setTextColor(Color.argb(180, 255, 255, 255))
            paint.letterSpacing = 0.08f
            setPadding(dpToPx(4), 0, 0, 0)
        }
        panel.addView(notifTitle, lp(MATCH, WRAP))
        addSpacer(panel, 8)

        val notifScroll = ScrollView(context)
        notifScroll.isScrollbarFadingEnabled = true
        notificationList = LinearLayout(context)
        notificationList!!.orientation = LinearLayout.VERTICAL
        notifScroll.addView(notificationList)
        panel.addView(notifScroll, lp(MATCH, 0, 1f))

        refreshNotificationList()

        // ---- Panel swipe-up dismiss gesture ----------------------------------
        panel.setOnTouchListener(object : View.OnTouchListener {
            private var downY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> downY = event.rawY
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - downY
                        if (dy < -dpToPx(40f)) hidePanel()
                    }
                }
                return false
            }
        })
    }

    // =========================================================================
    // Quick Settings Grid
    // =========================================================================

    private data class QSTile(
        val label: String,
        val iconRes: Int,
        var active: Boolean,
        val action: (Boolean) -> Unit
    )

    private fun buildQuickSettingsGrid(): LinearLayout {
        val tiles = listOf(
            QSTile("Wi-Fi", android.R.drawable.stat_sys_download, wifiManager.isWifiEnabled) { on ->
                try {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = on
                } catch (_: Exception) {}
            },
            QSTile("Bluetooth", android.R.drawable.ic_lock_power_off, bluetoothAdapter?.isEnabled == true) { on ->
                try {
                    if (on) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
                } catch (_: Exception) {}
            },
            QSTile("Airplane", android.R.drawable.ic_delete, isAirplaneModeOn()) { _ ->
                // Can only open settings for airplane mode (no programmatic toggle post-Android 4.2)
                val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            },
            QSTile("Torch", android.R.drawable.ic_menu_camera, false) { on ->
                toggleFlashlight(on)
            },
            QSTile("Rotation", android.R.drawable.ic_menu_rotate, isAutoRotateOn()) { on ->
                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION,
                        if (on) 1 else 0
                    )
                } catch (_: SecurityException) {}
            },
            QSTile("DND", android.R.drawable.ic_lock_silent_mode, isDndOn()) { _ ->
                val intent = Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            },
            QSTile("Battery", android.R.drawable.ic_lock_power_off, false) { _ ->
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            },
            QSTile("Settings", android.R.drawable.ic_menu_preferences, false) { _ ->
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        )

        val grid = LinearLayout(context)
        grid.orientation = LinearLayout.VERTICAL

        // 2 rows of 4
        for (row in 0 until 2) {
            val rowLayout = LinearLayout(context)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            for (col in 0 until 4) {
                val idx = row * 4 + col
                if (idx < tiles.size) {
                    rowLayout.addView(buildQSTile(tiles[idx]), lp(0, WRAP, 1f))
                }
            }
            grid.addView(rowLayout, lp(MATCH, WRAP))
            if (row == 0) addSpacer(grid, 12)
        }
        return grid
    }

    private fun buildQSTile(tile: QSTile): View {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10))

        val iconBg = FrameLayout(context)
        val bgSize = dpToPx(52)
        val iconBgLp = FrameLayout.LayoutParams(bgSize, bgSize)
        iconBgLp.gravity = Gravity.CENTER_HORIZONTAL
        iconBg.layoutParams = iconBgLp
        iconBg.background = buildRoundedBackground(
            if (tile.active) Color.argb(230, 255, 255, 255) else Color.argb(60, 255, 255, 255),
            dpToPx(16).toFloat()
        )

        val icon = ImageView(context)
        icon.setImageResource(tile.iconRes)
        val iconSize = dpToPx(26)
        val iconLp = FrameLayout.LayoutParams(iconSize, iconSize)
        iconLp.gravity = Gravity.CENTER
        icon.layoutParams = iconLp
        icon.setColorFilter(if (tile.active) Color.argb(255, 20, 20, 20) else Color.WHITE)
        iconBg.addView(icon)

        val label = TextView(context)
        label.text = tile.label
        label.textSize = 10f
        label.setTextColor(Color.argb(200, 255, 255, 255))
        label.gravity = Gravity.CENTER

        container.addView(iconBg)
        addSpacer(container, 6)
        container.addView(label)

        var isActive = tile.active

        container.setOnClickListener {
            vibrateHaptic()
            isActive = !isActive
            tile.action(isActive)
            iconBg.background = buildRoundedBackground(
                if (isActive) Color.argb(230, 255, 255, 255) else Color.argb(60, 255, 255, 255),
                dpToPx(16).toFloat()
            )
            icon.setColorFilter(if (isActive) Color.argb(255, 20, 20, 20) else Color.WHITE)
        }

        container.setOnLongClickListener {
            vibrateHaptic()
            true
        }

        return container
    }

    // =========================================================================
    // Notification list
    // =========================================================================

    private fun refreshNotificationList() {
        val list = notificationList ?: return
        list.removeAllViews()

        if (activeNotifications.isEmpty()) {
            val empty = TextView(context)
            empty.text = "No notifications"
            empty.textSize = 13f
            empty.setTextColor(Color.argb(120, 255, 255, 255))
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dpToPx(12), 0, dpToPx(12))
            list.addView(empty, lp(MATCH, WRAP))
            return
        }

        activeNotifications.take(10).forEach { notif ->
            list.addView(buildNotificationCard(notif), lp(MATCH, WRAP))
            addSpacer(list, 8)
        }
    }

    private fun buildNotificationCard(notif: NotificationItem): View {
        val card = FrameLayout(context)
        card.background = buildRoundedBackground(Color.argb(70, 255, 255, 255), dpToPx(14).toFloat())
        card.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        // App icon
        val appIcon = ImageView(context)
        val iconSize = dpToPx(36)
        val iconLp = LinearLayout.LayoutParams(iconSize, iconSize)
        iconLp.marginEnd = dpToPx(12)
        appIcon.layoutParams = iconLp
        notif.appIcon?.let { appIcon.setImageDrawable(it) }
        appIcon.background = buildRoundedBackground(Color.argb(40, 255, 255, 255), dpToPx(10).toFloat())
        appIcon.clipToOutline = true

        val textCol = LinearLayout(context)
        textCol.orientation = LinearLayout.VERTICAL
        val textLp = LinearLayout.LayoutParams(0, WRAP, 1f)
        textCol.layoutParams = textLp

        val titleView = TextView(context)
        titleView.text = notif.title
        titleView.textSize = 13f
        titleView.setTextColor(Color.WHITE)
        titleView.maxLines = 1
        titleView.paint.isFakeBoldText = true

        val bodyView = TextView(context)
        bodyView.text = notif.text
        bodyView.textSize = 12f
        bodyView.setTextColor(Color.argb(180, 255, 255, 255))
        bodyView.maxLines = 2

        textCol.addView(titleView)
        textCol.addView(bodyView)

        row.addView(appIcon)
        row.addView(textCol)

        // Dismiss X
        val dismiss = TextView(context)
        dismiss.text = "✕"
        dismiss.textSize = 14f
        dismiss.setTextColor(Color.argb(140, 255, 255, 255))
        dismiss.setPadding(dpToPx(8), 0, 0, 0)
        dismiss.setOnClickListener {
            removeNotification(notif.key)
            NovaNotificationListenerService.getInstance()?.cancelNotification(notif.key)
        }
        row.addView(dismiss)

        card.addView(row)
        return card
    }

    // =========================================================================
    // Slider row
    // =========================================================================

    private fun addSliderRow(
        parent: LinearLayout,
        label: String,
        icon: Drawable?,
        onChange: (Float) -> Unit
    ) {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = buildRoundedBackground(Color.argb(50, 255, 255, 255), dpToPx(14).toFloat())
        row.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))

        val iconView = ImageView(context)
        iconView.setImageDrawable(icon)
        iconView.setColorFilter(Color.WHITE)
        val iconLp = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
        iconLp.marginEnd = dpToPx(10)
        iconView.layoutParams = iconLp
        row.addView(iconView)

        val slider = FrostedSeekBar(context)
        slider.layoutParams = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
        slider.onValueChanged = onChange
        row.addView(slider)

        parent.addView(row, lp(MATCH, WRAP))
    }

    // =========================================================================
    // Show / Hide animation
    // =========================================================================

    fun showPanel() {
        if (panelVisible) return
        panelVisible = true

        val root = panelRoot ?: return
        root.translationY = -panelHeight.toFloat()
        root.alpha = 0f
        root.visibility = View.VISIBLE

        currentAnimator?.cancel()
        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                root.translationY = -panelHeight * (1f - f)
                root.alpha = f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Update window flags to allow focus (for sliders)
                    val lp = root.layoutParams as WindowManager.LayoutParams
                    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    wm.updateViewLayout(root, lp)
                }
            })
            start()
        }
        updateClock()
    }

    fun hidePanel() {
        if (!panelVisible) return
        panelVisible = false

        val root = panelRoot ?: return

        // Restore no-focus flag before hiding
        val lp = root.layoutParams as WindowManager.LayoutParams
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(root, lp)

        currentAnimator?.cancel()
        currentAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                root.translationY = -panelHeight * (1f - f)
                root.alpha = f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    root.visibility = View.INVISIBLE
                    onDismiss?.invoke()
                }
            })
            start()
        }
    }

    // =========================================================================
    // Clock helpers
    // =========================================================================

    private fun updateClock() {
        val timeFmt = SimpleDateFormat("h:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        val now = Date()
        clockLabel?.text = timeFmt.format(now)
        dateLabel?.text = dateFmt.format(now)
    }

    // =========================================================================
    // System state helpers
    // =========================================================================

    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun isAutoRotateOn(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun isDndOn(): Boolean {
        val nf = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return nf.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private var torchOn = false
    private var torchCameraId: String? = null

    private fun toggleFlashlight(on: Boolean) {
        try {
            if (torchCameraId == null) {
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                    ) {
                        torchCameraId = id
                        break
                    }
                }
            }
            torchCameraId?.let { cameraManager.setTorchMode(it, on) }
            torchOn = on
        } catch (_: CameraAccessException) {}
    }

    // =========================================================================
    // Haptics
    // =========================================================================

    private fun vibrateHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    // =========================================================================
    // Drawing helpers
    // =========================================================================

    private fun buildRoundedBackground(color: Int, radius: Float): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.setColor(color)
        shape.cornerRadius = radius
        return shape
    }

    // =========================================================================
    // Layout helpers
    // =========================================================================

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    private fun addSpacer(parent: LinearLayout, dp: Int) {
        val spacer = View(context)
        parent.addView(spacer, LinearLayout.LayoutParams(MATCH, dpToPx(dp)))
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}

// =============================================================================
// FrostedGlassView – simulated iOS frosted glass via semi-transparent dark overlay
// =============================================================================

class FrostedGlassView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 10, 10, 20)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

// =============================================================================
// FrostedSeekBar – custom pill-style slider matching iOS aesthetics
// =============================================================================

class FrostedSeekBar(context: Context) : View(context) {
    var value: Float = 0.5f
        private set
    var onValueChanged: ((Float) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(4f, 0f, 2f, Color.argb(80, 0, 0, 0))
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val r = h / 2f

        // Track
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, trackPaint)
        // Fill
        val fillW = w * value
        canvas.drawRoundRect(RectF(0f, 0f, fillW, h), r, r, fillPaint)
        // Thumb
        val cx = fillW.coerceIn(r, w - r)
        canvas.drawCircle(cx, h / 2f, r - 2, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                value = (event.x / width).coerceIn(0f, 1f)
                onValueChanged?.invoke(value)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

// =============================================================================
// NotificationItem – data model
// =============================================================================

data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val appIcon: Drawable?,
    val timestamp: Long = System.currentTimeMillis()
)

// =============================================================================
// NovaNotificationListenerService
// Must be declared in AndroidManifest.xml with BIND_NOTIFICATION_LISTENER_SERVICE permission.
// =============================================================================

class NovaNotificationListenerService : NotificationListenerService() {

    companion object {
        private var instance: WeakReference<NovaNotificationListenerService>? = null

        fun getInstance(): NovaNotificationListenerService? = instance?.get()
    }

    private var overlay: SystemUIOverlay? = null

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
    }

    fun bindOverlay(sysUiOverlay: SystemUIOverlay) {
        overlay = sysUiOverlay
    }

    fun unbindOverlay() {
        overlay = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val appIcon = try {
            packageManager.getApplicationIcon(sbn.packageName)
        } catch (_: Exception) { null }

        val item = NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            appIcon = appIcon,
            timestamp = sbn.postTime
        )
        overlay?.pushNotification(item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        overlay?.removeNotification(sbn.key)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
