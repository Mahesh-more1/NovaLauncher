package com.novalauncher.launcher.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.novalauncher.launcher.utils.AppPreferences
import com.novalauncher.launcher.R
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Full-featured SettingsActivity for NovaLauncher.
 *
 * Implements:
 * 1. Home Screen: Grid columns, icon size, show icon labels
 * 2. Physics & Fluid Dynamics: Enable toggle, gravity slider (-1.0 to 1.0),
 *    fluid drag slider (0.0 to 1.0), collision detection toggle, and a live interactive preview
 * 3. Appearance: Icon pack selector, wallpaper picker, dock style
 * 4. System Overlay: iOS Quick Settings toggle, Notification shade toggle
 * 5. Performance: Smooth animations toggle, Battery saver mode toggle
 * 6. About: Version display, GitHub repository link, Reset to defaults
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private var physicsPreviewView: PhysicsPreviewView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = AppPreferences.getInstance(this)

        initToolbar()
        initHomeScreenSection()
        initPhysicsSection()
        initAppearanceSection()
        initSystemOverlaySection()
        initPerformanceSection()
        initAboutSection()
    }

    override fun onResume() {
        super.onResume()
        physicsPreviewView?.start()
    }

    override fun onPause() {
        super.onPause()
        physicsPreviewView?.stop()
    }

    private fun initToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    // ── SECTION A: HOME SCREEN ──────────────────────────────────────────────

    private fun initHomeScreenSection() {
        // Grid Columns (2, 4, 5)
        val rowGrid = findViewById<View>(R.id.row_grid_columns)
        val titleGrid = rowGrid.findViewById<TextView>(R.id.pref_title)
        val subGrid = rowGrid.findViewById<TextView>(R.id.pref_subtitle)
        val iconGrid = rowGrid.findViewById<ImageView>(R.id.pref_icon)

        titleGrid.text = "Desktop Grid Columns"
        subGrid.text = "${prefs.gridColumns} Columns"
        iconGrid.setImageResource(R.drawable.ic_settings_tile)

        rowGrid.setOnClickListener {
            val options = arrayOf("2 Columns (Spacious)", "4 Columns (Standard)", "5 Columns (Dense)")
            val values = intArrayOf(2, 4, 5)
            val currentIndex = values.indexOf(prefs.gridColumns).coerceAtLeast(1)

            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Desktop Columns")
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    val chosen = values[which]
                    prefs.gridColumns = chosen
                    subGrid.text = "$chosen Columns"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Icon Size Slider (40dp to 72dp)
        val rowSize = findViewById<View>(R.id.row_icon_size)
        val titleSize = rowSize.findViewById<TextView>(R.id.pref_title)
        val subSize = rowSize.findViewById<TextView>(R.id.pref_subtitle)
        val valueSize = rowSize.findViewById<TextView>(R.id.pref_value)
        val seekSize = rowSize.findViewById<SeekBar>(R.id.pref_seekbar)
        val iconSize = rowSize.findViewById<ImageView>(R.id.pref_icon)

        titleSize.text = "Icon Size"
        subSize.text = "App icon scale on desktop and drawer"
        iconSize.setImageResource(R.drawable.ic_settings_tile)

        val currentSize = prefs.iconSize
        seekSize.max = 32 // 40dp to 72dp
        seekSize.progress = (currentSize - 40).coerceIn(0, 32)
        valueSize.text = "${currentSize}dp"

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeDp = 40 + progress
                valueSize.text = "${sizeDp}dp"
                if (fromUser) {
                    prefs.iconSize = sizeDp
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Show Icon Labels Toggle
        val rowLabels = findViewById<View>(R.id.row_show_labels)
        val titleLabels = rowLabels.findViewById<TextView>(R.id.pref_title)
        val subLabels = rowLabels.findViewById<TextView>(R.id.pref_subtitle)
        val switchLabels = rowLabels.findViewById<SwitchCompat>(R.id.pref_switch)
        val iconLabels = rowLabels.findViewById<ImageView>(R.id.pref_icon)

        titleLabels.text = "Show Icon Labels"
        subLabels.text = "Display app names below icons"
        iconLabels.setImageResource(R.drawable.ic_settings_tile)
        switchLabels.isChecked = prefs.showIconLabels

        rowLabels.setOnClickListener {
            val newVal = !switchLabels.isChecked
            switchLabels.isChecked = newVal
            prefs.showIconLabels = newVal
        }
    }

    // ── SECTION B: PHYSICS ──────────────────────────────────────────────────

    private fun initPhysicsSection() {
        // Enable Physics Toggle
        val rowEnable = findViewById<View>(R.id.row_physics_enable)
        val titleEnable = rowEnable.findViewById<TextView>(R.id.pref_title)
        val subEnable = rowEnable.findViewById<TextView>(R.id.pref_subtitle)
        val switchEnable = rowEnable.findViewById<SwitchCompat>(R.id.pref_switch)
        val iconEnable = rowEnable.findViewById<ImageView>(R.id.pref_icon)

        titleEnable.text = "Enable Physics Engine"
        subEnable.text = "Anti-gravity icon kinetics and verlet integration"
        iconEnable.setImageResource(R.drawable.ic_rotation)
        switchEnable.isChecked = prefs.physicsEnabled

        // Gravity Strength Slider (-1.0 to 1.0)
        val rowGravity = findViewById<View>(R.id.row_gravity_strength)
        val titleGravity = rowGravity.findViewById<TextView>(R.id.pref_title)
        val subGravity = rowGravity.findViewById<TextView>(R.id.pref_subtitle)
        val valueGravity = rowGravity.findViewById<TextView>(R.id.pref_value)
        val seekGravity = rowGravity.findViewById<SeekBar>(R.id.pref_seekbar)
        val iconGravity = rowGravity.findViewById<ImageView>(R.id.pref_icon)

        titleGravity.text = "Gravity Strength"
        subGravity.text = "Negative: Anti-gravity (up) | Positive: Normal (down)"
        iconGravity.setImageResource(R.drawable.ic_rotation)

        seekGravity.max = 200 // maps 0..200 to -1.0 .. 1.0
        val gravProgress = (((prefs.gravityStrength + 1.0f) / 2.0f) * 200).toInt().coerceIn(0, 200)
        seekGravity.progress = gravProgress
        valueGravity.text = String.format("%.2f G", prefs.gravityStrength)

        // Fluid Resistance Slider (0.0 to 1.0)
        val rowFluid = findViewById<View>(R.id.row_fluid_resistance)
        val titleFluid = rowFluid.findViewById<TextView>(R.id.pref_title)
        val subFluid = rowFluid.findViewById<TextView>(R.id.pref_subtitle)
        val valueFluid = rowFluid.findViewById<TextView>(R.id.pref_value)
        val seekFluid = rowFluid.findViewById<SeekBar>(R.id.pref_seekbar)
        val iconFluid = rowFluid.findViewById<ImageView>(R.id.pref_icon)

        titleFluid.text = "Fluid Resistance / Drag"
        subFluid.text = "Viscosity damping factor for floating items"
        iconFluid.setImageResource(R.drawable.ic_rotation)

        seekFluid.max = 100 // 0.0 to 1.0
        seekFluid.progress = (prefs.fluidResistance * 100).toInt().coerceIn(0, 100)
        valueFluid.text = String.format("%.2f", prefs.fluidResistance)

        // Collision Detection Toggle
        val rowCollision = findViewById<View>(R.id.row_collision_detection)
        val titleCollision = rowCollision.findViewById<TextView>(R.id.pref_title)
        val subCollision = rowCollision.findViewById<TextView>(R.id.pref_subtitle)
        val switchCollision = rowCollision.findViewById<SwitchCompat>(R.id.pref_switch)
        val iconCollision = rowCollision.findViewById<ImageView>(R.id.pref_icon)

        titleCollision.text = "Icon Collision Detection"
        subCollision.text = "Allows icons to bounce off each other and borders"
        iconCollision.setImageResource(R.drawable.ic_rotation)
        switchCollision.isChecked = prefs.collisionDetection

        // Setup Live Physics Preview View
        val previewContainer = findViewById<FrameLayout>(R.id.physics_preview_container)
        physicsPreviewView = PhysicsPreviewView(
            context = this,
            initialGravity = prefs.gravityStrength,
            initialFluidResistance = prefs.fluidResistance,
            initialCollisions = prefs.collisionDetection,
            initialEnabled = prefs.physicsEnabled
        )
        previewContainer.addView(physicsPreviewView)

        // Listeners for Physics Settings
        rowEnable.setOnClickListener {
            val newVal = !switchEnable.isChecked
            switchEnable.isChecked = newVal
            prefs.physicsEnabled = newVal
            physicsPreviewView?.setPhysicsEnabled(newVal)
        }

        seekGravity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val g = (progress / 100.0f) - 1.0f
                valueGravity.text = String.format("%.2f G", g)
                if (fromUser) {
                    prefs.gravityStrength = g
                    physicsPreviewView?.setGravity(g)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekFluid.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val drag = progress / 100.0f
                valueFluid.text = String.format("%.2f", drag)
                if (fromUser) {
                    prefs.fluidResistance = drag
                    physicsPreviewView?.setFluidResistance(drag)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        rowCollision.setOnClickListener {
            val newVal = !switchCollision.isChecked
            switchCollision.isChecked = newVal
            prefs.collisionDetection = newVal
            physicsPreviewView?.setCollisionDetection(newVal)
        }
    }

    // ── SECTION C: APPEARANCE ───────────────────────────────────────────────

    private fun initAppearanceSection() {
        // Icon Pack Selector
        val rowPack = findViewById<View>(R.id.row_icon_pack)
        val titlePack = rowPack.findViewById<TextView>(R.id.pref_title)
        val subPack = rowPack.findViewById<TextView>(R.id.pref_subtitle)
        val iconPack = rowPack.findViewById<ImageView>(R.id.pref_icon)

        titlePack.text = "Icon Pack"
        subPack.text = if (prefs.iconPack == "default") "System Default" else prefs.iconPack
        iconPack.setImageResource(R.drawable.ic_settings_tile)

        rowPack.setOnClickListener {
            showIconPackSelector(subPack)
        }

        // Wallpaper Picker
        val rowWallpaper = findViewById<View>(R.id.row_wallpaper_picker)
        val titleWallpaper = rowWallpaper.findViewById<TextView>(R.id.pref_title)
        val subWallpaper = rowWallpaper.findViewById<TextView>(R.id.pref_subtitle)
        val iconWallpaper = rowWallpaper.findViewById<ImageView>(R.id.pref_icon)

        titleWallpaper.text = "Change Wallpaper"
        subWallpaper.text = "Open system wallpaper selector"
        iconWallpaper.setImageResource(R.drawable.ic_settings_tile)

        rowWallpaper.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                startActivity(Intent.createChooser(intent, "Select Wallpaper"))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open wallpaper picker", Toast.LENGTH_SHORT).show()
            }
        }

        // Dock Style
        val rowDock = findViewById<View>(R.id.row_dock_style)
        val titleDock = rowDock.findViewById<TextView>(R.id.pref_title)
        val subDock = rowDock.findViewById<TextView>(R.id.pref_subtitle)
        val iconDock = rowDock.findViewById<ImageView>(R.id.pref_icon)

        titleDock.text = "Dock Style"
        subDock.text = prefs.dockStyle.replaceFirstChar { it.uppercase() }
        iconDock.setImageResource(R.drawable.ic_settings_tile)

        rowDock.setOnClickListener {
            val styles = arrayOf("Frosted (iOS Blur)", "Solid Dark", "Fully Transparent")
            val rawValues = arrayOf(
                AppPreferences.DOCK_STYLE_FROSTED,
                AppPreferences.DOCK_STYLE_SOLID,
                AppPreferences.DOCK_STYLE_TRANSPARENT
            )
            val currentIdx = rawValues.indexOf(prefs.dockStyle).coerceAtLeast(0)

            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Dock Style")
                .setSingleChoiceItems(styles, currentIdx) { dialog, which ->
                    val chosen = rawValues[which]
                    prefs.dockStyle = chosen
                    subDock.text = styles[which]
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showIconPackSelector(subtitleView: TextView) {
        val pm = packageManager
        val iconPackIntents = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("com.gau.go.launcherex.theme"),
            Intent("com.novalauncher.THEME")
        )

        val installedPacks = mutableListOf<Pair<String, String>>()
        installedPacks.add("System Default" to "default")

        for (intent in iconPackIntents) {
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (ri in resolveInfos) {
                val label = ri.loadLabel(pm).toString()
                val pkg = ri.activityInfo.packageName
                if (installedPacks.none { it.second == pkg }) {
                    installedPacks.add(label to pkg)
                }
            }
        }

        val names = installedPacks.map { it.first }.toTypedArray()
        val currentIdx = installedPacks.indexOfFirst { it.second == prefs.iconPack }.coerceAtLeast(0)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Icon Pack")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                val selected = installedPacks[which]
                prefs.iconPack = selected.second
                subtitleView.text = selected.first
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── SECTION D: SYSTEM OVERLAY ───────────────────────────────────────────

    private fun initSystemOverlaySection() {
        // iOS Quick Settings Toggle
        val rowQS = findViewById<View>(R.id.row_quick_settings)
        val titleQS = rowQS.findViewById<TextView>(R.id.pref_title)
        val subQS = rowQS.findViewById<TextView>(R.id.pref_subtitle)
        val switchQS = rowQS.findViewById<SwitchCompat>(R.id.pref_switch)
        val iconQS = rowQS.findViewById<ImageView>(R.id.pref_icon)

        titleQS.text = "iOS Quick Settings Panel"
        subQS.text = "Pull down from top-right to reveal control center"
        iconQS.setImageResource(R.drawable.ic_wifi)
        switchQS.isChecked = prefs.enableQuickSettings

        rowQS.setOnClickListener {
            val newVal = !switchQS.isChecked
            switchQS.isChecked = newVal
            prefs.enableQuickSettings = newVal
        }

        // Notification Shade Toggle
        val rowNotif = findViewById<View>(R.id.row_notification_shade)
        val titleNotif = rowNotif.findViewById<TextView>(R.id.pref_title)
        val subNotif = rowNotif.findViewById<TextView>(R.id.pref_subtitle)
        val switchNotif = rowNotif.findViewById<SwitchCompat>(R.id.pref_switch)
        val iconNotif = rowNotif.findViewById<ImageView>(R.id.pref_icon)

        titleNotif.text = "Notification Shade"
        subNotif.text = "Pull down from top-left or center to view grouped notifications"
        iconNotif.setImageResource(R.drawable.ic_dnd)
        switchNotif.isChecked = prefs.enableNotificationShade

        rowNotif.setOnClickListener {
            val newVal = !switchNotif.isChecked
            switchNotif.isChecked = newVal
            prefs.enableNotificationShade = newVal
        }
    }

    // ── SECTION E: PERFORMANCE ──────────────────────────────────────────────

    private fun initPerformanceSection() {
        // Smooth Animations Toggle
        val rowAnim = findViewById<View>(R.id.row_smooth_animations)
        val titleAnim = rowAnim.findViewById<TextView>(R.id.pref_title)
        val subAnim = rowAnim.findViewById<TextView>(R.id.pref_subtitle)
        val switchAnim = rowAnim.findViewById<SwitchCompat>(R.id.pref_switch)
        val iconAnim = rowAnim.findViewById<ImageView>(R.id.pref_icon)

        titleAnim.text = "Smooth 60/120 FPS Animations"
        subAnim.text = "Hardware-accelerated transitions and physics interpolation"
        iconAnim.setImageResource(R.drawable.ic_settings_tile)
        switchAnim.isChecked = prefs.smoothAnimations

        rowAnim.setOnClickListener {
            val newVal = !switchAnim.isChecked
            switchAnim.isChecked = newVal
            prefs.smoothAnimations = newVal
        }

        // Battery Saver Mode Toggle
        val rowBattery = findViewById<View>(R.id.row_battery_saver)
        val titleBattery = rowBattery.findViewById<TextView>(R.id.pref_title)
        val subBattery = rowBattery.findViewById<TextView>(R.id.pref_subtitle)
        val switchBattery = rowBattery.findViewById<SwitchCompat>(R.id.pref_switch)
        val iconBattery = rowBattery.findViewById<ImageView>(R.id.pref_icon)

        titleBattery.text = "Battery Saver Mode"
        subBattery.text = "Limits background physics updates and reduces blur depth"
        iconBattery.setImageResource(R.drawable.ic_battery)
        switchBattery.isChecked = prefs.batterySaver

        rowBattery.setOnClickListener {
            val newVal = !switchBattery.isChecked
            switchBattery.isChecked = newVal
            prefs.batterySaver = newVal
        }
    }

    // ── SECTION F: ABOUT ────────────────────────────────────────────────────

    private fun initAboutSection() {
        // Version
        val rowVersion = findViewById<View>(R.id.row_about_version)
        val titleVer = rowVersion.findViewById<TextView>(R.id.pref_title)
        val subVer = rowVersion.findViewById<TextView>(R.id.pref_subtitle)
        val iconVer = rowVersion.findViewById<ImageView>(R.id.pref_icon)

        titleVer.text = "NovaLauncher Version"
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            subVer.text = "v${pInfo.versionName} (Build ${pInfo.versionCode})"
        } catch (e: Exception) {
            subVer.text = "v1.0.0 (Release)"
        }
        iconVer.setImageResource(R.drawable.ic_settings_tile)

        // GitHub Link
        val rowGithub = findViewById<View>(R.id.row_about_github)
        val titleGit = rowGithub.findViewById<TextView>(R.id.pref_title)
        val subGit = rowGithub.findViewById<TextView>(R.id.pref_subtitle)
        val iconGit = rowGithub.findViewById<ImageView>(R.id.pref_icon)

        titleGit.text = "Source Code & Contributions"
        subGit.text = "https://github.com/novalauncher/NovaLauncher"
        iconGit.setImageResource(R.drawable.ic_settings_tile)

        rowGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/novalauncher/NovaLauncher"))
            startActivity(intent)
        }

        // Reset to Defaults
        val rowReset = findViewById<View>(R.id.row_about_reset)
        val titleReset = rowReset.findViewById<TextView>(R.id.pref_title)
        val subReset = rowReset.findViewById<TextView>(R.id.pref_subtitle)
        val iconReset = rowReset.findViewById<ImageView>(R.id.pref_icon)

        titleReset.text = "Reset to Defaults"
        subReset.text = "Restore all launcher settings to default configuration"
        iconReset.setImageResource(R.drawable.ic_settings_tile)

        rowReset.setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Reset Settings")
                .setMessage("Are you sure you want to reset all NovaLauncher preferences to default values?")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.resetToDefaults()
                    Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ── LIVE PHYSICS PREVIEW VIEW ───────────────────────────────────────────

    /**
     * Self-contained interactive preview canvas demonstrating Verlet physics,
     * fluid drag, gravity inversion, and collision detection in real-time.
     */
    private class PhysicsPreviewView(
        context: Context,
        private var gravity: Float,
        private var fluidResistance: Float,
        private var collisions: Boolean,
        private var isPhysicsEnabled: Boolean
    ) : View(context) {

        private data class Particle(
            var x: Float,
            var y: Float,
            var prevX: Float,
            var prevY: Float,
            val radius: Float,
            val color: Int
        )

        private val particles = mutableListOf<Particle>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#33FFFFFF")
        }

        private var running = false
        private val frameRunnable = object : Runnable {
            override fun run() {
                if (running) {
                    stepPhysics()
                    invalidate()
                    postDelayed(this, 16L) // ~60 FPS
                }
            }
        }

        init {
            val colors = intArrayOf(
                Color.parseColor("#007AFF"), // iOS Blue
                Color.parseColor("#34C759"), // iOS Green
                Color.parseColor("#FF9500"), // iOS Orange
                Color.parseColor("#AF52DE"), // iOS Purple
                Color.parseColor("#5AC8FA")  // iOS Teal
            )
            for (i in 0 until 5) {
                val rad = 14f + (i * 2f)
                particles.add(
                    Particle(
                        x = 60f + (i * 50f),
                        y = 40f + (i * 15f),
                        prevX = 60f + (i * 50f),
                        prevY = 40f + (i * 15f),
                        radius = rad,
                        color = colors[i % colors.size]
                    )
                )
            }
        }

        fun start() {
            if (!running) {
                running = true
                post(frameRunnable)
            }
        }

        fun stop() {
            running = false
            removeCallbacks(frameRunnable)
        }

        fun setGravity(g: Float) { gravity = g }
        fun setFluidResistance(drag: Float) { fluidResistance = drag }
        fun setCollisionDetection(enable: Boolean) { collisions = enable }
        fun setPhysicsEnabled(enable: Boolean) { isPhysicsEnabled = enable }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isPhysicsEnabled) return super.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Impulse away from touch point
                    for (p in particles) {
                        val dx = p.x - event.x
                        val dy = p.y - event.y
                        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        if (dist < 180f) {
                            val force = (180f - dist) / 180f * 18f
                            p.prevX -= (dx / dist) * force
                            p.prevY -= (dy / dist) * force
                        }
                    }
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun stepPhysics() {
            if (!isPhysicsEnabled || width == 0 || height == 0) return

            val w = width.toFloat()
            val h = height.toFloat()
            val dt = 0.5f
            val effGravY = gravity * 0.8f // scaled gravity
            val dragDamping = (1.0f - (fluidResistance * 0.08f)).coerceIn(0.85f, 0.999f)

            // Verlet integration
            for (p in particles) {
                var vx = (p.x - p.prevX) * dragDamping
                var vy = (p.y - p.prevY) * dragDamping

                val nextX = p.x + vx
                val nextY = p.y + vy + (effGravY * dt * dt)

                p.prevX = p.x
                p.prevY = p.y
                p.x = nextX
                p.y = nextY

                // Bounds constraint
                if (p.x - p.radius < 0f) {
                    p.x = p.radius
                    p.prevX = p.x + vx * 0.6f
                } else if (p.x + p.radius > w) {
                    p.x = w - p.radius
                    p.prevX = p.x + vx * 0.6f
                }

                if (p.y - p.radius < 0f) {
                    p.y = p.radius
                    p.prevY = p.y + vy * 0.6f
                } else if (p.y + p.radius > h) {
                    p.y = h - p.radius
                    p.prevY = p.y + vy * 0.6f
                }
            }

            // Particle-particle collision resolution
            if (collisions) {
                val n = particles.size
                for (i in 0 until n) {
                    val p1 = particles[i]
                    for (j in i + 1 until n) {
                        val p2 = particles[j]
                        val dx = p2.x - p1.x
                        val dy = p2.y - p1.y
                        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)
                        val minDist = p1.radius + p2.radius
                        if (dist < minDist) {
                            val overlap = 0.5f * (minDist - dist)
                            val nx = dx / dist
                            val ny = dy / dist
                            p1.x -= nx * overlap
                            p1.y -= ny * overlap
                            p2.x += nx * overlap
                            p2.y += ny * overlap
                        }
                    }
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Draw background boundary
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

            if (!isPhysicsEnabled) {
                paint.color = Color.parseColor("#44FFFFFF")
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("Physics Disabled", width / 2f, height / 2f + 10f, paint)
                return
            }

            // Draw particles
            for (p in particles) {
                paint.color = p.color
                canvas.drawCircle(p.x, p.y, p.radius, paint)
            }
        }
    }
}
