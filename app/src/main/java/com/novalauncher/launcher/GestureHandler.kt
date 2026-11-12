package com.novalauncher.launcher

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Custom gesture handler that wraps [GestureDetector] and translates raw
 * [MotionEvent]s into high-level launcher gestures.
 *
 * Supported gestures
 * ──────────────────
 * • Swipe UP   → open app drawer
 * • Swipe DOWN → show notification shade
 * • Swipe LEFT → next home-screen page
 * • Swipe RIGHT→ previous home-screen page
 * • Long press → enter edit mode / drag
 * • Double tap → trigger a user-defined action (e.g. lock screen)
 *
 * Usage
 * ─────
 *   val handler = GestureHandler(context, listener)
 *   override fun onTouchEvent(e: MotionEvent) = handler.onTouchEvent(e)
 */
class GestureHandler(
    context: android.content.Context,
    private val listener: GestureListener
) {

    // ── Public callback interface ────────────────────────────────────────────

    interface GestureListener {
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onLongPress(e: MotionEvent)
        fun onDoubleTap(e: MotionEvent)
        fun onSingleTap(e: MotionEvent)
    }

    // ── Configuration ────────────────────────────────────────────────────────

    private val viewConfig = ViewConfiguration.get(context)
    private val minSwipeVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
    private val touchSlop        = viewConfig.scaledTouchSlop.toFloat()

    /** Minimum pixel distance to qualify as a swipe. */
    private val minSwipeDistance: Float = 80f   // dp-independent (pixel threshold at ~160dpi)

    // ── Internal GestureDetector ─────────────────────────────────────────────

    private val internalListener = object : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean = true  // must return true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            listener.onSingleTap(e)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            listener.onDoubleTap(e)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            listener.onLongPress(e)
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val e1Safe = e1 ?: return false

            val dx = e2.x - e1Safe.x
            val dy = e2.y - e1Safe.y

            val isHorizontal = abs(dx) > abs(dy)

            if (isHorizontal) {
                if (abs(dx) < minSwipeDistance || abs(velocityX) < minSwipeVelocity) return false
                if (dx < 0) listener.onSwipeLeft() else listener.onSwipeRight()
            } else {
                if (abs(dy) < minSwipeDistance || abs(velocityY) < minSwipeVelocity) return false
                if (dy < 0) listener.onSwipeUp() else listener.onSwipeDown()
            }
            return true
        }
    }

    private val gestureDetector = GestureDetector(context, internalListener).apply {
        setIsLongpressEnabled(true)
    }

    // ── Public entry point ───────────────────────────────────────────────────

    /**
     * Forward the activity / view's [MotionEvent] stream here.
     * Returns true if the event was consumed.
     */
    fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)
}
