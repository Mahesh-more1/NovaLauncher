package com.novalauncher.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.novalauncher.launcher.AppInfo
import com.novalauncher.launcher.R
import com.novalauncher.launcher.utils.AppPreferences
import java.util.Locale

/**
 * Full-screen bottom sheet fragment displaying all installed applications in NovaLauncher.
 *
 * Features:
 * - Animated slide-up bottom sheet matching iOS / modern launcher aesthetics.
 * - 4-column GridLayoutManager.
 * - Real-time search filter with clear and voice search inputs.
 * - Fast-scrolling vertical A-Z alphabet side index.
 * - Alphabetically sorted application list.
 * - Tap to launch application with smooth dismiss.
 * - Long-press to open the options dialog (Open, Add to Dock, Add Widget, App Info, Uninstall).
 */
class AppDrawerFragment : BottomSheetDialogFragment() {

    private var recyclerView: RecyclerView? = null
    private var searchEditText: EditText? = null
    private var btnClearSearch: View? = null
    private var btnVoiceSearch: View? = null
    private var alphabetIndexLayout: LinearLayout? = null
    private var emptySearchView: View? = null

    private lateinit var drawerAdapter: DrawerAppAdapter
    private val allApps = mutableListOf<AppInfo>()
    private val filteredApps = mutableListOf<AppInfo>()

    // Map each alphabet char ('A'..'Z', '#') to first matching adapter position
    private val alphabetMap = mutableMapOf<Char, Int>()

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val query = matches?.firstOrNull() ?: ""
            searchEditText?.setText(query)
            searchEditText?.setSelection(query.length)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
                behavior.peekHeight = resources.displayMetrics.heightPixels
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.activity_app_drawer, container, false)

        recyclerView = root.findViewById(R.id.drawer_recycler_view)
        searchEditText = root.findViewById(R.id.search_edit_text)
        btnClearSearch = root.findViewById(R.id.btn_clear_search)
        btnVoiceSearch = root.findViewById(R.id.btn_voice_search)
        alphabetIndexLayout = root.findViewById(R.id.alphabet_index_layout)
        emptySearchView = root.findViewById(R.id.empty_search_view)

        setupRecyclerView()
        setupSearch()
        loadInstalledApps()

        return root
    }

    private fun setupRecyclerView() {
        val ctx = requireContext()
        drawerAdapter = DrawerAppAdapter(
            onItemClick = { app ->
                launchApp(app)
                dismiss()
            },
            onItemLongClick = { app, anchorView ->
                showAppOptionsDialog(app)
            }
        )

        recyclerView?.apply {
            layoutManager = GridLayoutManager(ctx, 4)
            adapter = drawerAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun setupSearch() {
        searchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch?.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterApps(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch?.setOnClickListener {
            searchEditText?.setText("")
        }

        btnVoiceSearch?.setOnClickListener {
            launchVoiceSearch()
        }
    }

    private fun launchVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_hint))
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice search not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInstalledApps() {
        val ctx = context ?: return
        val pm = ctx.packageManager

        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        allApps.clear()
        var index = 0
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            val activityName = info.activityInfo.name
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)

            allApps.add(
                AppInfo(
                    packageName = pkg,
                    activityName = activityName,
                    label = label,
                    icon = icon,
                    position = index++
                )
            )
        }

        // Sort alphabetically case-insensitively
        allApps.sortBy { it.label.lowercase(Locale.getDefault()) }

        filterApps("")
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        if (query.isBlank()) {
            filteredApps.addAll(allApps)
        } else {
            val lower = query.lowercase(Locale.getDefault())
            for (app in allApps) {
                if (app.label.lowercase(Locale.getDefault()).contains(lower) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(lower)
                ) {
                    filteredApps.add(app)
                }
            }
        }

        drawerAdapter.setItems(filteredApps)
        emptySearchView?.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE

        buildAlphabetIndex()
    }

    private fun buildAlphabetIndex() {
        val layout = alphabetIndexLayout ?: return
        layout.removeAllViews()
        alphabetMap.clear()

        if (filteredApps.isEmpty()) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE

        // Map characters to positions
        for ((idx, app) in filteredApps.withIndex()) {
            val firstChar = app.label.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar else '#'
            if (!alphabetMap.containsKey(key)) {
                alphabetMap[key] = idx
            }
        }

        val letters = mutableListOf<Char>()
        for (c in 'A'..'Z') {
            letters.add(c)
        }
        letters.add('#')

        val ctx = requireContext()
        val density = resources.displayMetrics.density

        for (letter in letters) {
            val tv = TextView(ctx).apply {
                text = letter.toString()
                textSize = 9f
                setTextColor(if (alphabetMap.containsKey(letter)) Color.WHITE else Color.parseColor("#4DFFFFFF"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, (1 * density).toInt(), 0, (1 * density).toInt())
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    val pos = alphabetMap[letter]
                    if (pos != null) {
                        (recyclerView?.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(pos, 0)
                    }
                }
            }
            layout.addView(tv)
        }
    }

    private fun launchApp(app: AppInfo) {
        val ctx = context ?: return
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Unable to launch app", Toast.LENGTH_SHORT).show()
        }
    }

    // ── App Options Dialog ───────────────────────────────────────────────────

    private fun showAppOptionsDialog(app: AppInfo) {
        val ctx = context ?: return
        val dialog = BottomSheetDialog(ctx)
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_app_options, null)

        val ivIcon: ImageView = dialogView.findViewById(R.id.dialog_app_icon)
        val tvTitle: TextView = dialogView.findViewById(R.id.dialog_app_title)
        val tvPackage: TextView = dialogView.findViewById(R.id.dialog_app_package)

        ivIcon.setImageDrawable(app.icon)
        tvTitle.text = app.label
        tvPackage.text = app.packageName

        // Option: Open
        dialogView.findViewById<View>(R.id.option_open).setOnClickListener {
            dialog.dismiss()
            launchApp(app)
            dismiss()
        }

        // Option: Add to Dock
        dialogView.findViewById<View>(R.id.option_add_to_dock).setOnClickListener {
            dialog.dismiss()
            val currentDock = AppPreferences.getDockApps(ctx).toMutableList()
            if (currentDock.contains(app.packageName)) {
                Toast.makeText(ctx, "${app.label} is already in the dock", Toast.LENGTH_SHORT).show()
            } else {
                if (currentDock.size >= 4) {
                    currentDock[3] = app.packageName
                } else {
                    currentDock.add(app.packageName)
                }
                AppPreferences.setDockApps(ctx, currentDock)
                Toast.makeText(ctx, "Pinned ${app.label} to dock", Toast.LENGTH_SHORT).show()
            }
        }

        // Option: Add Widget
        dialogView.findViewById<View>(R.id.option_add_widget).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(ctx, "Open widgets on home screen", Toast.LENGTH_SHORT).show()
        }

        // Option: App Info
        dialogView.findViewById<View>(R.id.option_app_info).setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not open App Info", Toast.LENGTH_SHORT).show()
            }
        }

        // Option: Uninstall
        dialogView.findViewById<View>(R.id.option_uninstall).setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:${app.packageName}")
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Toast.makeText(ctx, "Could not uninstall app", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView = null
        searchEditText = null
        btnClearSearch = null
        btnVoiceSearch = null
        alphabetIndexLayout = null
        emptySearchView = null
    }

    // ── Internal Adapter ─────────────────────────────────────────────────────

    private class DrawerAppAdapter(
        private val onItemClick: (AppInfo) -> Unit,
        private val onItemLongClick: (AppInfo, View) -> Unit
    ) : RecyclerView.Adapter<DrawerAppAdapter.DrawerViewHolder>() {

        private val items = mutableListOf<AppInfo>()

        fun setItems(newItems: List<AppInfo>) {
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = items.size
                override fun getNewListSize(): Int = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return items[oldItemPosition].packageName == newItems[newItemPosition].packageName
                }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return items[oldItemPosition].label == newItems[newItemPosition].label
                }
            })
            items.clear()
            items.addAll(newItems)
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_icon, parent, false)
            return DrawerViewHolder(view)
        }

        override fun onBindViewHolder(holder: DrawerViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class DrawerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.app_icon)
            private val labelView: TextView = itemView.findViewById(R.id.app_label)

            fun bind(app: AppInfo) {
                labelView.text = app.label
                iconView.setImageDrawable(app.icon)

                itemView.setOnClickListener {
                    onItemClick(app)
                }

                itemView.setOnLongClickListener {
                    onItemLongClick(app, itemView)
                    true
                }
            }
        }
    }
}
