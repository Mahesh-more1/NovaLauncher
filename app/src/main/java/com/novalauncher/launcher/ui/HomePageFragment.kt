package com.novalauncher.launcher.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novalauncher.launcher.AppAdapter
import com.novalauncher.launcher.AppInfo
import com.novalauncher.launcher.PhysicsEngine
import com.novalauncher.launcher.R

/**
 * Fragment representing a single page of the NovaLauncher home screen.
 *
 * Hosts a 4-column [RecyclerView] with physics engine integration for icon animations,
 * drag-and-drop shortcut management, and handles an empty placeholder state.
 */
class HomePageFragment : Fragment() {

    private var pageIndex: Int = 0
    private var physicsEngine: PhysicsEngine? = null

    private var recyclerView: RecyclerView? = null
    private var emptyPlaceholder: View? = null
    private var appAdapter: AppAdapter? = null

    private val shortcutsList = mutableListOf<AppInfo>()

    var onAppClickListener: ((AppInfo) -> Unit)? = null
    var onAppLongClickListener: ((AppInfo, View) -> Unit)? = null

    companion object {
        private const val ARG_PAGE_INDEX = "arg_page_index"

        fun newInstance(pageIndex: Int): HomePageFragment {
            val fragment = HomePageFragment()
            val args = Bundle().apply {
                putInt(ARG_PAGE_INDEX, pageIndex)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageIndex = arguments?.getInt(ARG_PAGE_INDEX, 0) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_home_page, container, false)
        recyclerView = root.findViewById(R.id.home_grid_recycler)
        emptyPlaceholder = root.findViewById(R.id.empty_page_placeholder)
        setupRecyclerView()
        updatePlaceholderVisibility()
        return root
    }

    /**
     * Attaches or updates the [PhysicsEngine] instance for icon dynamics.
     */
    fun setPhysicsEngine(engine: PhysicsEngine) {
        this.physicsEngine = engine
        if (isAdded && context != null && recyclerView != null) {
            setupRecyclerView()
        }
    }

    private fun setupRecyclerView() {
        val ctx = context ?: return
        val rv = recyclerView ?: return

        val engine = physicsEngine ?: PhysicsEngine().also { it.start() }

        val adapter = AppAdapter(
            context = ctx,
            physicsEngine = engine,
            onAppLaunched = { app ->
                onAppClickListener?.invoke(app) ?: launchAppIntent(app)
            }
        )

        rv.layoutManager = GridLayoutManager(ctx, 4)
        rv.adapter = adapter
        adapter.itemTouchHelper.attachToRecyclerView(rv)
        this.appAdapter = adapter

        adapter.submitList(shortcutsList)
    }

    private fun launchAppIntent(app: AppInfo) {
        val ctx = context ?: return
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Shortcut Management ──────────────────────────────────────────────────

    fun setShortcuts(apps: List<AppInfo>) {
        shortcutsList.clear()
        shortcutsList.addAll(apps)
        appAdapter?.submitList(shortcutsList.toList())
        updatePlaceholderVisibility()
    }

    fun addShortcut(app: AppInfo) {
        if (shortcutsList.none { it.packageName == app.packageName }) {
            shortcutsList.add(app)
            appAdapter?.submitList(shortcutsList.toList())
            updatePlaceholderVisibility()
        }
    }

    fun removeShortcut(packageName: String) {
        val removed = shortcutsList.removeAll { it.packageName == packageName }
        if (removed) {
            appAdapter?.submitList(shortcutsList.toList())
            updatePlaceholderVisibility()
        }
    }

    fun getShortcuts(): List<AppInfo> = shortcutsList.toList()

    fun getPageIndex(): Int = pageIndex

    private fun updatePlaceholderVisibility() {
        emptyPlaceholder?.visibility = if (shortcutsList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView = null
        emptyPlaceholder = null
        appAdapter = null
    }
}
