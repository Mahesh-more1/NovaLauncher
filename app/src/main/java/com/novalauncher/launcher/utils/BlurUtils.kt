package com.novalauncher.launcher.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.util.LruCache
import android.view.View
import androidx.annotation.FloatRange
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-performance, memory-safe bitmap blurring utility for frosted glass effects.
 *
 * Features:
 * - RenderScript blur acceleration with automatic fallback to high-efficiency CPU BoxBlur/StackBlur.
 * - View capture to blurred frosted glass bitmap with downsampling for fluid 60fps rendering.
 * - LruCache to avoid redundant blurring of identical frames and dimensions.
 * - Memory safety: recycles temporary bitmaps and handles hardware bitmaps gracefully.
 */
object BlurUtils {

    private const val TAG = "BlurUtils"
    private const val DEFAULT_BLUR_RADIUS = 20f
    private const val DEFAULT_DOWNSCALE_FACTOR = 0.25f

    // Allocate ~1/16th of maximum available memory to the blur cache
    private val maxCacheSizeKb = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceAtLeast(4096)

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // Only recycle if it's strictly being evicted and not reused
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    /**
     * Clear all cached blurred bitmaps and release memory.
     */
    fun clearCache() {
        bitmapCache.evictAll()
    }

    /**
     * Blurs a bitmap with a specified radius.
     * Checks the LRU cache first to prevent redundant re-blurring.
     *
     * @param bitmap Source bitmap to blur
     * @param radius Blur radius (1.0f to 25.0f for standard Gaussian blur)
     * @return New blurred Bitmap
     */
    fun blurBitmap(bitmap: Bitmap, @FloatRange(from = 1.0, to = 25.0) radius: Float): Bitmap {
        return blurBitmapInternal(null, bitmap, radius)
    }

    /**
     * Blurs a bitmap with context for RenderScript acceleration.
     *
     * @param context Android context
     * @param bitmap Source bitmap to blur
     * @param radius Blur radius (1.0f to 25.0f)
     * @return New blurred Bitmap
     */
    fun blurBitmap(context: Context, bitmap: Bitmap, @FloatRange(from = 1.0, to = 25.0) radius: Float): Bitmap {
        return blurBitmapInternal(context, bitmap, radius)
    }

    /**
     * Captures a view's visual appearance and produces a frosted glass blurred bitmap.
     * Automatically downsamples the view hierarchy before blurring to minimize GPU/CPU overhead.
     *
     * @param view View to capture
     * @param context Context for rendering
     * @param radius Blur radius (default 20f)
     * @param downscaleFactor Downscale ratio between 0.1f and 0.5f (default 0.25f)
     * @return Blurred frosted glass bitmap
     */
    fun createFrostedGlassBitmap(
        view: View,
        context: Context,
        @FloatRange(from = 1.0, to = 25.0) radius: Float = DEFAULT_BLUR_RADIUS,
        @FloatRange(from = 0.1, to = 1.0) downscaleFactor: Float = DEFAULT_DOWNSCALE_FACTOR
    ): Bitmap {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            // View has not been laid out yet, return 1x1 dummy bitmap
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val cacheKey = "view_${view.id}_${width}x${height}_${radius}_${downscaleFactor}"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val scaledWidth = max(1, (width * downscaleFactor).roundToInt())
        val scaledHeight = max(1, (height * downscaleFactor).roundToInt())

        val captureBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(captureBitmap)
        canvas.scale(downscaleFactor, downscaleFactor)
        view.draw(canvas)

        val blurred = blurBitmapInternal(context, captureBitmap, radius)

        // If blurBitmap created a new instance, recycle the intermediate scaled capture
        if (blurred !== captureBitmap && !captureBitmap.isRecycled) {
            captureBitmap.recycle()
        }

        bitmapCache.put(cacheKey, blurred)
        return blurred
    }

    private fun blurBitmapInternal(context: Context?, source: Bitmap, radius: Float): Bitmap {
        val clampedRadius = radius.coerceIn(1f, 25f)
        val cacheKey = "bmp_${source.hashCode()}_${source.width}x${source.height}_${clampedRadius}"

        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        // Ensure source bitmap is in a software-mutable format (ARGB_8888)
        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }

        val output = Bitmap.createBitmap(softwareBitmap.width, softwareBitmap.height, Bitmap.Config.ARGB_8888)

        var renderedWithRenderScript = false
        // Try RenderScript if context is supplied and API level is <= 30 (RenderScript is deprecated in API 31+)
        if (context != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                var rs: RenderScript? = null
                var inputAlloc: Allocation? = null
                var outputAlloc: Allocation? = null
                var blurScript: ScriptIntrinsicBlur? = null
                try {
                    rs = RenderScript.create(context.applicationContext)
                    inputAlloc = Allocation.createFromBitmap(rs, softwareBitmap)
                    outputAlloc = Allocation.createFromBitmap(rs, output)
                    blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
                    blurScript.setRadius(clampedRadius)
                    blurScript.setInput(inputAlloc)
                    blurScript.forEach(outputAlloc)
                    outputAlloc.copyTo(output)
                    renderedWithRenderScript = true
                } finally {
                    blurScript?.destroy()
                    inputAlloc?.destroy()
                    outputAlloc?.destroy()
                    rs?.destroy()
                }
            } catch (e: Exception) {
                Log.w(TAG, "RenderScript blur failed, falling back to CPU BoxBlur: ${e.message}")
            }
        }

        if (!renderedWithRenderScript) {
            // High efficiency custom BoxBlur / StackBlur fallback
            applyBoxBlur(softwareBitmap, output, clampedRadius.roundToInt())
        }

        // Clean up temporary copy if we created one for hardware bitmaps
        if (softwareBitmap !== source && !softwareBitmap.isRecycled) {
            softwareBitmap.recycle()
        }

        bitmapCache.put(cacheKey, output)
        return output
    }

    /**
     * Production-grade multi-pass BoxBlur algorithm with edge clamping.
     * Operates in O(N) linear time per pixel pass.
     */
    private fun applyBoxBlur(src: Bitmap, dst: Bitmap, radius: Int) {
        val w = src.width
        val h = src.height
        val r = radius.coerceAtLeast(1)

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val temp = IntArray(w * h)

        // Horizontal pass
        boxBlurHorizontal(pixels, temp, w, h, r)
        // Vertical pass
        boxBlurVertical(temp, pixels, w, h, r)

        dst.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val windowSize = r + r + 1
        for (y in 0 until h) {
            val yOffset = y * w
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            // Pre-fill window with edge clamping
            for (i in -r..r) {
                val clampedX = i.coerceIn(0, w - 1)
                val pixel = src[yOffset + clampedX]
                sumA += (pixel ushr 24) and 0xFF
                sumR += (pixel ushr 16) and 0xFF
                sumG += (pixel ushr 8) and 0xFF
                sumB += pixel and 0xFF
            }

            for (x in 0 until w) {
                dst[yOffset + x] = ((sumA / windowSize) shl 24) or
                        ((sumR / windowSize) shl 16) or
                        ((sumG / windowSize) shl 8) or
                        (sumB / windowSize)

                // Slide window
                val leftX = (x - r).coerceIn(0, w - 1)
                val rightX = (x + r + 1).coerceIn(0, w - 1)

                val leftPixel = src[yOffset + leftX]
                val rightPixel = src[yOffset + rightX]

                sumA += ((rightPixel ushr 24) and 0xFF) - ((leftPixel ushr 24) and 0xFF)
                sumR += ((rightPixel ushr 16) and 0xFF) - ((leftPixel ushr 16) and 0xFF)
                sumG += ((rightPixel ushr 8) and 0xFF) - ((leftPixel ushr 8) and 0xFF)
                sumB += (rightPixel and 0xFF) - (leftPixel and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val windowSize = r + r + 1
        for (x in 0 until w) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            // Pre-fill window with edge clamping
            for (i in -r..r) {
                val clampedY = i.coerceIn(0, h - 1)
                val pixel = src[clampedY * w + x]
                sumA += (pixel ushr 24) and 0xFF
                sumR += (pixel ushr 16) and 0xFF
                sumG += (pixel ushr 8) and 0xFF
                sumB += pixel and 0xFF
            }

            for (y in 0 until h) {
                dst[y * w + x] = ((sumA / windowSize) shl 24) or
                        ((sumR / windowSize) shl 16) or
                        ((sumG / windowSize) shl 8) or
                        (sumB / windowSize)

                val topY = (y - r).coerceIn(0, h - 1)
                val bottomY = (y + r + 1).coerceIn(0, h - 1)

                val topPixel = src[topY * w + x]
                val bottomPixel = src[bottomY * w + x]

                sumA += ((bottomPixel ushr 24) and 0xFF) - ((topPixel ushr 24) and 0xFF)
                sumR += ((bottomPixel ushr 16) and 0xFF) - ((topPixel ushr 16) and 0xFF)
                sumG += ((bottomPixel ushr 8) and 0xFF) - ((topPixel ushr 8) and 0xFF)
                sumB += (bottomPixel and 0xFF) - (topPixel and 0xFF)
            }
        }
    }
}

