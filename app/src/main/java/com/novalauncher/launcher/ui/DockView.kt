package com.novalauncher.launcher.ui

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.util.AttributeSet
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.novalauncher.launcher.AppInfo
import com.novalauncher.launcher.R
import com.novalauncher.launcher.utils.AppPreferences

/**
 * Custom ViewGroup rendering the NovaLauncher bottom dock bar.
 *
 * Features:
 * - 4 customizable pinned app slots + 1 center app drawer shortcut.
 * - Frosted glass pill background with 24dp corner radius.
 * - Interactive Drag & Drop: drag an app from the home grid into a dock slot to pin.
 * - Persists pinned dock apps to [AppPreferences].
 * - App icon elevation with smooth shadows.
 * - Tap to launch app, long-press to unpin / remove from dock.
 */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ── UI Components ────────────────────────────────────────────────────────
    private val dockCard: CardView
    private val slotViews = ArrayList<FrameLayout>(4)
    private val iconViews = ArrayList<ImageView>(4)
    private val badgeViews = ArrayList<View>(4)
    private val drawerSlot: FrameLayout
    private val drawerIcon: ImageView

    // ── Data ─────────────────────────────────────────────────────────────────
    private val pinnedApps = ArrayList<AppInfo?>(4)

    // ── Callbacks ────────────────────────────────────────────────────────────
    var onAppDrawerClickListener: (() -> Unit)? = null
    var onDockAppClickListener: ((AppInfo) -> Unit)? = null
    var onDockAppRemovedListener: ((String) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_dock, this, true)

        dockCard = findViewById(R.id.dock_card_container)
        drawerSlot = findViewById(R.id.dock_slot_drawer)
        drawerIcon = findViewById(R.id.dock_drawer_icon)

        // Initialize 4 app slots
        val slotIds = intArrayOf(R.id.dock_slot_0, R.id.dock_slot_1, R.id.dock_slot_2, R.id.dock_slot_3)
        val iconIds = intArrayOf(R.id.dock_icon_0, R.id.dock_icon_1, R.id.dock_icon_2, R.id.dock_icon_3)
        val badgeIds = intArrayOf(R.id.dock_badge_0, R.id.dock_badge_1, R.id.dock_badge_2, R.id.dock_badge_3)

        for (i in 0 until 4) {
            val slotView: FrameLayout = findViewById(slotIds[i])
            val iconView: ImageView = findViewById(iconIds[i])
            val badgeView: View = findViewById(badgeIds[i])

            slotViews.add(slotView)
            iconViews.add(iconView)
            badgeViews.add(badgeView)
            pinnedApps.add(null)

            val slotIndex = i

            // Icon shadow elevation
            iconView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.22f)
                }
            }
            iconView.clipToOutline = true
            iconView.elevation = dpToPx(4f)

            // Tap to launch
            slotView.setOnClickListener {
                val app = pinnedApps[slotIndex]
                if (app != null) {
                    onDockAppClickListener?.invoke(app) ?: launchApp(app.packageName)
                }
            }

            // Long press to remove
            slotView.setOnLongClickListener {
                val app = pinnedApps[slotIndex]
                if (app != null) {
                    removeDockApp(slotIndex)
                    true
                } else {
                    false
                }
            }

            // Drag and drop target for pinning
            setupDragAndDrop(slotView, slotIndex)
        }

        // Center app drawer button
        drawerSlot.setOnClickListener {
            onAppDrawerClickListener?.invoke()
        }

        // Initialize AppPreferences and load stored dock items
        AppPreferences.init(context)
        loadStoredDockApps()
    }

    // ── Drag & Drop Handling ─────────────────────────────────────────────────

    private fun setupDragAndDrop(slotView: View, slotIndex: Int) {
        slotView.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Accept plain text or intent data
                    event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                            event.localState != null
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()

                    var pkgName: String? = null
                    val localState = event.localState
                    if (localState is AppInfo) {
                        pkgName = localState.packageName
                    } else if (localState is String) {
                        pkgName = localState
                    } else if (event.clipData != null && event.clipData.itemCount > 0) {
                        pkgName = event.clipData.getItemAt(0).text?.toString()
                    }

                    if (!pkgName.isNullOrBlank()) {
                        pinAppToSlot(pkgName, slotIndex)
                        true
                    } else {
                        false
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    true
                }
                else -> false
            }
        }
    }

    // ── App Pinning & Persistence ────────────────────────────────────────────

    fun pinAppToSlot(packageName: String, slotIndex: Int) {
        if (slotIndex !in 0 until 4) return
        val appInfo = resolveAppInfo(packageName) ?: return

        pinnedApps[slotIndex] = appInfo
        renderSlot(slotIndex)
        persistDockApps()
        Toast.makeText(context, "${appInfo.label} pinned to dock", Toast.LENGTH_SHORT).show()
    }

    fun removeDockApp(slotIndex: Int) {
        if (slotIndex !in 0 until 4) return
        val app = pinnedApps[slotIndex] ?: return

        pinnedApps[slotIndex] = null
        renderSlot(slotIndex)
        persistDockApps()
        onDockAppRemovedListener?.invoke(app.packageName)
        Toast.makeText(context, "${app.label} removed from dock", Toast.LENGTH_SHORT).show()
    }

    fun setBadge(slotIndex: Int, visible: Boolean) {
        if (slotIndex in 0 until 4) {
            badgeViews[slotIndex].visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun loadStoredDockApps() {
        val storedList = AppPreferences.getDockApps(context)
        for (i in 0 until 4) {
            if (i < storedList.size && storedList[i].isNotBlank()) {
                pinnedApps[i] = resolveAppInfo(storedList[i])
            } else {
                pinnedApps[i] = null
            }
            renderSlot(i)
        }
    }

    private fun persistDockApps() {
        val listToStore = mutableListOf<String>()
        for (app in pinnedApps) {
            if (app != null) {
                listToStore.add(app.packageName)
            }
        }
        AppPreferences.setDockApps(context, listToStore)
    }

    private fun renderSlot(index: Int) {
        val app = pinnedApps[index]
        val iconView = iconViews[index]
        if (app != null && app.icon != null) {
            iconView.setImageDrawable(app.icon)
            iconView.alpha = 1.0f
            slotViews[index].contentDescription = app.label
        } else {
            iconView.setImageDrawable(null)
            iconView.alpha = 0f
            slotViews[index].contentDescription = "Empty dock slot"
        }
    }

    private fun resolveAppInfo(packageName: String): AppInfo? {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            val intent = pm.getLaunchIntentForPackage(packageName)
            val activityName = intent?.component?.className ?: ""
            AppInfo(
                packageName = packageName,
                activityName = activityName,
                label = label,
                icon = icon
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
