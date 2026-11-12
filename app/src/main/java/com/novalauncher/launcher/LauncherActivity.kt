package com.novalauncher.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.StatusBarManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

/**
 * Main home-screen activity for NovaLauncher.
 *
 * Responsibilities
 * ────────────────
 * • Acts as the system HOME launcher (declared in AndroidManifest with
 *   MAIN + HOME intent-filter).
 * • Binds the live wallpaper via [WallpaperManager].
 * • Loads installed apps off the main thread and populates an [AppAdapter]
 *   backed by a [RecyclerView] grid.
 * • Integrates [PhysicsEngine] for icon physics animations.
 * • Routes touch events through [GestureHandler]:
 *     – Swipe UP   → open app drawer (slide RecyclerView into view)
 *     – Swipe DOWN → expand notification shade
 * • Hosts a search bar that filters the app grid in real-time.
 * • Memory-safe: all long-lived references are wrapped in [WeakReference];
 *   the [CoroutineScope] is cancelled in [onDestroy].
 */
class LauncherActivity : Activity() {

    // ── Views ────────────────────────────────────────────────────────────────

    private lateinit var wallpaperView: ImageView
    private lateinit var drawerContainer: View
    private lateinit var appRecyclerView: RecyclerView
    private lateinit var searchBar: EditText

    // ── Core components ──────────────────────────────────────────────────────

    private lateinit var physicsEngine: PhysicsEngine
    private lateinit var appAdapter: AppAdapter
    private lateinit var gestureHandler: GestureHandler

    // ── State ────────────────────────────────────────────────────────────────

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var allApps: List<AppInfo> = emptyList()
    private var drawerOpen = false

    // Keep weak reference to avoid leaking context in lambdas stored elsewhere
    private val selfRef: WeakReference<LauncherActivity> get() = WeakReference(this)

    // ── Broadcast receiver: listen for installed/removed apps ────────────────

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_REMOVED -> loadApps()
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen — let the wallpaper show through
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.activity_launcher)

        wallpaperView    = findViewById(R.id.wallpaper_view)
        drawerContainer  = findViewById(R.id.drawer_container)
        appRecyclerView  = findViewById(R.id.app_recycler_view)
        searchBar        = findViewById(R.id.search_bar)

        initWallpaper()
        initPhysicsEngine()
        initRecyclerView()
        initGestureHandler()
        initSearchBar()
        registerPackageReceiver()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        physicsEngine.start()
    }

    override fun onPause() {
        super.onPause()
        physicsEngine.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        physicsEngine.destroy()
        activityScope.cancel()
        try { unregisterReceiver(packageReceiver) } catch (_: IllegalArgumentException) { }
    }

    // Back press on the launcher should do nothing (we ARE the home screen)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* intentionally empty */ }

    // ── Touch delegation ─────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureHandler.onTouchEvent(event) || super.onTouchEvent(event)
    }

    // ── Initialisation helpers ────────────────────────────────────────────────

    private fun initWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(this)
            val drawable = wm.drawable
            wallpaperView.setImageDrawable(drawable)
        } catch (e: SecurityException) {
            // READ_EXTERNAL_STORAGE not granted — leave transparent
        }
    }

    private fun initPhysicsEngine() {
        physicsEngine = PhysicsEngine(maxBodies = 128).also {
            val dm = resources.displayMetrics
            it.setWorldSize(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
            it.setGravity(0f, 0f)   // icons float by default; tap adds impulse
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initRecyclerView() {
        appAdapter = AppAdapter(this, physicsEngine) { appInfo ->
            launchApp(appInfo)
        }

        appRecyclerView.apply {
            layoutManager = GridLayoutManager(this@LauncherActivity, 4)
            adapter = appAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        // Drawer starts hidden below the screen
        drawerContainer.translationY = resources.displayMetrics.heightPixels.toFloat()
        drawerContainer.visibility = View.VISIBLE
    }

    private fun initGestureHandler() {
        gestureHandler = GestureHandler(this, object : GestureHandler.GestureListener {
            override fun onSwipeUp()    = openDrawer()
            override fun onSwipeDown()  = expandNotifications()
            override fun onSwipeLeft()  = Unit  // pager handled by ViewPager2 elsewhere
            override fun onSwipeRight() = Unit
            override fun onLongPress(e: MotionEvent) = Unit
            override fun onDoubleTap(e: MotionEvent) = Unit
            override fun onSingleTap(e: MotionEvent) {
                if (drawerOpen) closeDrawer()
            }
        })
    }

    private fun initSearchBar() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter(s?.toString() ?: "", allApps)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    // ── App loading ───────────────────────────────────────────────────────────

    private fun loadApps() {
        activityScope.launch {
            val apps = withContext(Dispatchers.IO) {
                queryInstalledApps()
            }
            allApps = apps
            appAdapter.submitList(apps)
        }
    }

    private fun queryInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolved.mapIndexed { index, info ->
            AppInfo(
                packageName  = info.activityInfo.packageName,
                activityName = info.activityInfo.name,
                label        = info.loadLabel(pm).toString(),
                icon         = info.loadIcon(pm),
                position     = index
            )
        }.sortedBy { it.label }
    }

    // ── Drawer animation ──────────────────────────────────────────────────────

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        searchBar.text.clear()
        drawerContainer.animate()
            .translationY(0f)
            .setDuration(320)
            .withEndAction { searchBar.requestFocus() }
            .start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        searchBar.clearFocus()
        val offscreen = resources.displayMetrics.heightPixels.toFloat()
        drawerContainer.animate()
            .translationY(offscreen)
            .setDuration(280)
            .start()
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: use StatusBarManager (requires EXPAND_STATUS_BAR permission)
                val sbm = getSystemService(StatusBarManager::class.java)
                // expandNotificationsPanel is hidden API; fall back to broadcast
                sendBroadcast(Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"))
            }
            // Fallback for all versions: bring down the shade via reflection
            @SuppressLint("WrongConstant")
            val sbService = getSystemService("statusbar")
            if (sbService != null) {
                val cls = Class.forName("android.app.StatusBarManager")
                val expand = cls.getMethod("expandNotificationsPanel")
                expand.invoke(sbService)
            }
        } catch (_: Exception) {
            // Silently fail — STATUS_BAR permission or reflection not available
        }
    }

    // ── App launch ────────────────────────────────────────────────────────────

    private fun launchApp(appInfo: AppInfo) {
        val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
        closeDrawer()
    }
}
