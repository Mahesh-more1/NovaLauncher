package com.novalauncher.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference

/**
 * RecyclerView adapter for the NovaLauncher app drawer grid.
 *
 * Features
 * ────────
 * • Grid of app icons loaded via [BitmapFactory] (no Glide / Picasso).
 * • Physics-driven icon translation animations via [PhysicsEngine].
 * • Long-press drag & drop reordering backed by [ItemTouchHelper].
 * • Tap to launch the selected app.
 * • Full [DiffUtil] support for efficient list updates.
 * • Weak references to [Context] to avoid leaking the Activity.
 */
class AppAdapter(
    context: Context,
    private val physicsEngine: PhysicsEngine,
    private val onAppLaunched: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private val contextRef = WeakReference(context)
    private val items = mutableListOf<AppInfo>()

    // Map appInfo.id → physics body id
    private val physicsBodyIds = HashMap<String, Int>()

    // ItemTouchHelper for drag-and-drop
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    val itemTouchHelper = ItemTouchHelper(DragCallback())

    // ── ViewHolder ───────────────────────────────────────────────────────────

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.app_icon)
        val labelView: TextView = itemView.findViewById(R.id.app_label)

        // Physics body assigned to this ViewHolder's current item
        var physicsBodyId: Int = -1

        fun bind(app: AppInfo) {
            labelView.text = app.label

            // ── Icon loading with BitmapFactory ──────────────────────────────
            // We draw the drawable to a Bitmap at a fixed 96 × 96 px size,
            // then set it directly on the ImageView.  Avoids Glide/Picasso.
            val ctx = contextRef.get()
            if (ctx != null && app.icon != null) {
                val bmp = drawableToBitmap(app.icon, 96, 96)
                iconView.setImageBitmap(bmp)
            } else {
                iconView.setImageDrawable(app.icon)
            }

            // ── Physics binding ───────────────────────────────────────────────
            physicsBodyId = physicsBodyIds.getOrPut(app.id) {
                physicsEngine.addBody(
                    x = itemView.x,
                    y = itemView.y,
                    mass = 1f,
                    radius = 48f,
                    dragCoeff = 0.05f,
                    buoyancy = 0f,
                    onPositionChanged = { px, py ->
                        // Offset from initial layout position, applied as translation
                        itemView.post {
                            itemView.translationX = px - itemView.left
                            itemView.translationY = py - itemView.top
                        }
                    }
                )
            }

            // ── Tap to launch ─────────────────────────────────────────────────
            itemView.setOnClickListener {
                // Tiny spring impulse for tactile feedback
                physicsEngine.applyForce(physicsBodyId, 0f, -300f)
                onAppLaunched(app)
            }

            // ── Long-press for drag ───────────────────────────────────────────
            itemView.setOnLongClickListener {
                recyclerViewRef?.get()?.let { rv ->
                    itemTouchHelper.startDrag(rv.findViewHolderForAdapterPosition(bindingAdapterPosition)
                        as? RecyclerView.ViewHolder ?: return@let)
                }
                true
            }
        }
    }

    // ── Adapter overrides ────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_icon, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Replace the current app list with [newList], computing diffs automatically.
     */
    fun submitList(newList: List<AppInfo>) {
        val diff = DiffUtil.calculateDiff(AppDiffCallback(items, newList))
        items.clear()
        items.addAll(newList)
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Filter the displayed apps by [query] (matches label or package name).
     * Pass an empty string to clear the filter.
     */
    fun filter(query: String, fullList: List<AppInfo>) {
        val filtered = if (query.isBlank()) fullList
        else fullList.filter {
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
        submitList(filtered)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerViewRef = WeakReference(recyclerView)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerViewRef = null
        itemTouchHelper.attachToRecyclerView(null)
    }

    override fun onViewRecycled(holder: AppViewHolder) {
        super.onViewRecycled(holder)
        // Reset any physics-driven translation so recycled views start clean
        holder.itemView.translationX = 0f
        holder.itemView.translationY = 0f
    }

    // ── Drag & drop callback ─────────────────────────────────────────────────

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to   = target.bindingAdapterPosition
            if (from == RecyclerView.NO_ID.toInt() || to == RecyclerView.NO_ID.toInt()) return false
            items[from].position = to
            items[to].position   = from
            items.add(to, items.removeAt(from))
            notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Swipe-to-dismiss not supported on the grid; no-op.
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1f
            viewHolder.itemView.scaleX = 1f
            viewHolder.itemView.scaleY = 1f
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.apply {
                    alpha  = 0.85f
                    scaleX = 1.08f
                    scaleY = 1.08f
                }
            }
        }
    }

    // ── DiffUtil callback ────────────────────────────────────────────────────

    private class AppDiffCallback(
        private val old: List<AppInfo>,
        private val new: List<AppInfo>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(op: Int, np: Int) = old[op].id == new[np].id
        override fun areContentsTheSame(op: Int, np: Int) = old[op] == new[np]
    }

    // ── Bitmap utility ───────────────────────────────────────────────────────

    private fun drawableToBitmap(drawable: Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }
}
