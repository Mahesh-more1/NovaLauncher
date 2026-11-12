package com.novalauncher.launcher.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max

/**
 * Custom View that renders iOS-style animated page indicator dots and pills.
 *
 * Features:
 * - Smooth pill expansion for the currently active page.
 * - Interpolated color transitions between active and inactive states.
 * - Connects directly to ViewPager2 page scroll events via [onPageScrolled].
 * - Supports manual click-to-switch navigation.
 */
class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Configuration ────────────────────────────────────────────────────────
    private var pageCount: Int = 3
    private var currentPage: Int = 0
    private var scrollPosition: Int = 0
    private var scrollOffset: Float = 0f

    private val dotRadius: Float = dpToPx(3.5f)
    private val dotDiameter: Float = dotRadius * 2f
    private val pillExpandedWidth: Float = dpToPx(18f)
    private val dotSpacing: Float = dpToPx(8f)

    private var activeColor: Int = Color.WHITE
    private var inactiveColor: Int = Color.parseColor("#66FFFFFF")

    private val argbEvaluator = ArgbEvaluator()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rectF = RectF()

    var onPageClickListener: ((Int) -> Unit)? = null

    init {
        isClickable = true
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun setPageCount(count: Int) {
        val newCount = max(0, count)
        if (pageCount != newCount) {
            pageCount = newCount
            if (currentPage >= pageCount) {
                currentPage = max(0, pageCount - 1)
            }
            visibility = if (pageCount <= 1) GONE else VISIBLE
            requestLayout()
            invalidate()
        }
    }

    fun getPageCount(): Int = pageCount

    fun setCurrentPage(index: Int, smooth: Boolean = true) {
        if (index !in 0 until pageCount) return
        if (!smooth) {
            currentPage = index
            scrollPosition = index
            scrollOffset = 0f
            invalidate()
            return
        }

        val startPos = scrollPosition + scrollOffset
        val targetPos = index.toFloat()
        val animator = ValueAnimator.ofFloat(startPos, targetPos).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                scrollPosition = fraction.toInt()
                scrollOffset = fraction - scrollPosition
                invalidate()
            }
        }
        currentPage = index
        animator.start()
    }

    fun getCurrentPage(): Int = currentPage

    /**
     * Called by ViewPager2.OnPageChangeCallback.onPageScrolled.
     */
    fun onPageScrolled(position: Int, positionOffset: Float) {
        scrollPosition = position
        scrollOffset = positionOffset
        invalidate()
    }

    // ── Measurement ──────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (pageCount <= 0) {
            setMeasuredDimension(0, 0)
            return
        }

        // Total width: (pageCount - 1) * dotDiameter + pillExpandedWidth + (pageCount - 1) * dotSpacing
        val contentWidth = if (pageCount > 1) {
            (pageCount - 1) * dotDiameter + pillExpandedWidth + (pageCount - 1) * dotSpacing
        } else {
            pillExpandedWidth
        }

        val desiredWidth = (contentWidth + paddingLeft + paddingRight).toInt()
        val desiredHeight = (dotDiameter * 2f + paddingTop + paddingBottom).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount <= 1) return

        val centerY = (height / 2f)

        // Compute total width based on current animated widths
        val itemWidths = FloatArray(pageCount) { dotDiameter }
        val itemColors = IntArray(pageCount) { inactiveColor }

        val activeIndex = scrollPosition
        val nextIndex = scrollPosition + 1

        val currentWeight = 1f - scrollOffset
        val nextWeight = scrollOffset

        if (activeIndex in 0 until pageCount) {
            itemWidths[activeIndex] = dotDiameter + (pillExpandedWidth - dotDiameter) * currentWeight
            itemColors[activeIndex] = argbEvaluator.evaluate(currentWeight, inactiveColor, activeColor) as Int
        }

        if (nextIndex in 0 until pageCount) {
            itemWidths[nextIndex] = dotDiameter + (pillExpandedWidth - dotDiameter) * nextWeight
            itemColors[nextIndex] = argbEvaluator.evaluate(nextWeight, inactiveColor, activeColor) as Int
        }

        var totalDrawnWidth = 0f
        for (i in 0 until pageCount) {
            totalDrawnWidth += itemWidths[i]
            if (i < pageCount - 1) totalDrawnWidth += dotSpacing
        }

        var startX = (width - totalDrawnWidth) / 2f

        for (i in 0 until pageCount) {
            val itemW = itemWidths[i]
            val color = itemColors[i]

            dotPaint.color = color
            rectF.set(
                startX,
                centerY - dotRadius,
                startX + itemW,
                centerY + dotRadius
            )
            canvas.drawRoundRect(rectF, dotRadius, dotRadius, dotPaint)

            startX += itemW + dotSpacing
        }
    }

    // ── Touch Events ─────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && pageCount > 1) {
            val clickX = event.x
            val step = width.toFloat() / pageCount
            val clickedIndex = (clickX / step).toInt().coerceIn(0, pageCount - 1)
            if (clickedIndex != currentPage) {
                setCurrentPage(clickedIndex, true)
                onPageClickListener?.invoke(clickedIndex)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
