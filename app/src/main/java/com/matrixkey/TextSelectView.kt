package com.matrixkey

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * The scanned photo with every recognised word outlined. Tap a word to add or
 * remove it; drag across words to select a run of them (like Gboard's text
 * scanner). Pinch, or the + and − buttons, to zoom in; with one finger off
 * the words you can then move the zoomed photo around.
 * [onChange] gets the selected words in reading order.
 */
@SuppressLint("ViewConstructor")
class TextSelectView(context: Context, private val accent: Int) : View(context) {

    class Word(val text: String, val box: Rect, val line: Int)

    var onChange: ((String) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private var words: List<Word> = emptyList()
    private var selected = BooleanArray(0)
    /** Scale from photo coordinates to the displayed bitmap. */
    private var photoToBitmap = 1f
    private val dst = RectF()
    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * dp }
    private val shade = Paint().apply { color = 0x66000000 }
    private val btnBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }
    private val btnFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    // Zoom: screen = base * zoom + pan, where base is the photo fitted to the view.
    private var zoom = 1f
    private var panX = 0f; private var panY = 0f
    private val maxZoom = 6f
    private val plusBtn = RectF(); private val minusBtn = RectF()

    /** [photoWidth] is the width the boxes were measured in (the recognised image). */
    fun set(bmp: Bitmap, photoWidth: Int, list: List<Word>) {
        bitmap = bmp
        words = list
        selected = BooleanArray(list.size) { true }
        zoom = 1f; panX = 0f; panY = 0f
        photoToBitmap = bmp.width / photoWidth.toFloat()
        requestLayout(); invalidate()
        emit()
    }

    fun selectAll(on: Boolean) {
        java.util.Arrays.fill(selected, on)
        invalidate(); emit()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val b = bitmap
        val maxH = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        val h = if (b == null) 0 else minOf(maxH, (w * b.height / b.width.toFloat()).toInt())
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val scale = minOf(width / b.width.toFloat(), height / b.height.toFloat())
        val dw = b.width * scale; val dh = b.height * scale
        dst.set((width - dw) / 2, (height - dh) / 2, (width + dw) / 2, (height + dh) / 2)
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom)
        canvas.drawBitmap(b, null, dst, paint)
        canvas.drawRect(dst, shade)
        val r = RectF()
        stroke.strokeWidth = 1.5f * dp / zoom
        val rad = 4 * dp / zoom
        words.forEachIndexed { i, w ->
            boxOnScreen(w, r)
            if (selected[i]) {
                fill.color = ColorUtils.setAlphaComponent(accent, 110)
                canvas.drawRoundRect(r, rad, rad, fill)
                stroke.color = accent
            } else {
                stroke.color = 0xAAFFFFFF.toInt()
            }
            canvas.drawRoundRect(r, rad, rad, stroke)
        }
        canvas.restore()
        drawZoomButtons(canvas)
    }

    private fun drawZoomButtons(canvas: Canvas) {
        val s = 40 * dp; val m = 10 * dp
        plusBtn.set(width - m - s, height - m - s, width - m, height - m)
        minusBtn.set(plusBtn.left - 8 * dp - s, plusBtn.top, plusBtn.left - 8 * dp, plusBtn.bottom)
        btnFg.strokeWidth = 2.5f * dp
        for ((rect, plus) in listOf(minusBtn to false, plusBtn to true)) {
            val enabled = if (plus) zoom < maxZoom - 0.01f else zoom > 1.01f
            btnFg.alpha = if (enabled) 255 else 90
            canvas.drawOval(rect, btnBg)
            val cx = rect.centerX(); val cy = rect.centerY(); val a = 8 * dp
            canvas.drawLine(cx - a, cy, cx + a, cy, btnFg)
            if (plus) canvas.drawLine(cx, cy - a, cx, cy + a, btnFg)
        }
    }

    /** A word's box in base (unzoomed) coordinates. */
    private fun boxOnScreen(w: Word, out: RectF) {
        val b = bitmap ?: return
        val s = (dst.width() / b.width) * photoToBitmap
        out.set(dst.left + w.box.left * s, dst.top + w.box.top * s, dst.left + w.box.right * s, dst.top + w.box.bottom * s)
        out.inset(-2 * dp / zoom, -2 * dp / zoom)
    }

    private fun wordAt(sx: Float, sy: Float): Int {
        val x = (sx - panX) / zoom; val y = (sy - panY) / zoom
        val r = RectF()
        var best = -1; var bestD = Float.MAX_VALUE
        words.forEachIndexed { i, w ->
            boxOnScreen(w, r)
            r.inset(-6 * dp / zoom, -6 * dp / zoom)
            if (r.contains(x, y)) {
                val d = kotlin.math.abs(r.centerX() - x) + kotlin.math.abs(r.centerY() - y)
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    /** Zooms by [factor] keeping the screen point (fx, fy) still. */
    private fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val nz = (zoom * factor).coerceIn(1f, maxZoom)
        val f = nz / zoom
        panX = fx - (fx - panX) * f
        panY = fy - (fy - panY) * f
        zoom = nz
        clampPan(); invalidate()
    }

    private fun clampPan() {
        if (zoom <= 1.001f) { zoom = 1f; panX = 0f; panY = 0f; return }
        // Keep the photo covering the view (or centred where it's smaller).
        fun axis(pan: Float, lo: Float, hi: Float, size: Float): Float {
            val a = lo * zoom; val b = hi * zoom
            return if (b - a <= size) (size - (a + b)) / 2 else pan.coerceIn(size - b, -a)
        }
        panX = axis(panX, dst.left, dst.right, width.toFloat())
        panY = axis(panY, dst.top, dst.bottom, height.toFloat())
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomBy(d.scaleFactor, d.focusX, d.focusY); return true
        }
    })

    private enum class Mode { NONE, SELECT, PAN, PINCH, BUTTON }
    private var mode = Mode.NONE
    private var anchor = -1
    private var dragged = false
    private var downX = 0f; private var downY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var before = BooleanArray(0)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bitmap == null) return false
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                dragged = false
                if (plusBtn.contains(e.x, e.y) || minusBtn.contains(e.x, e.y)) {
                    mode = Mode.BUTTON
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                anchor = wordAt(e.x, e.y)
                before = selected.copyOf()
                mode = when {
                    anchor >= 0 -> Mode.SELECT
                    zoom > 1f -> Mode.PAN
                    else -> return false   // let the page scroll
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger: it's a pinch. Undo anything the first finger just dragged.
                if (mode == Mode.SELECT && dragged) { before.copyInto(selected); emit() }
                mode = Mode.PINCH; anchor = -1; lastX = -1f
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.SELECT -> {
                    if (!dragged && kotlin.math.hypot(e.x - downX, e.y - downY) < 8 * dp) return true
                    dragged = true
                    val cur = wordAt(e.x, e.y)
                    if (cur >= 0) {
                        // Drag picks the run of words from where you started to here.
                        val lo = minOf(anchor, cur); val hi = maxOf(anchor, cur)
                        for (i in selected.indices) selected[i] = i in lo..hi
                        invalidate(); emit()
                    }
                }
                Mode.PAN -> {
                    panX += e.x - lastX; panY += e.y - lastY
                    clampPan(); invalidate()
                }
                Mode.PINCH -> if (e.pointerCount >= 2) {
                    // Two-finger drag also moves the photo.
                    val fx = (e.getX(0) + e.getX(1)) / 2; val fy = (e.getY(0) + e.getY(1)) / 2
                    if (lastX >= 0) { panX += fx - lastX; panY += fy - lastY; clampPan(); invalidate() }
                    lastX = fx; lastY = fy
                    return true
                }
                else -> {}
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.SELECT -> if (!dragged) { selected[anchor] = !selected[anchor]; invalidate(); emit() }
                    Mode.BUTTON -> when {
                        plusBtn.contains(e.x, e.y) -> zoomBy(1.75f, width / 2f, height / 2f)
                        minusBtn.contains(e.x, e.y) -> zoomBy(1 / 1.75f, width / 2f, height / 2f)
                    }
                    else -> {}
                }
                mode = Mode.NONE; anchor = -1
            }
            MotionEvent.ACTION_POINTER_UP -> { lastX = -1f }
            MotionEvent.ACTION_CANCEL -> { mode = Mode.NONE; anchor = -1 }
        }
        if (mode != Mode.PINCH) { lastX = e.x; lastY = e.y }
        return true
    }

    private fun emit() {
        val sb = StringBuilder()
        var lastLine = -1
        words.forEachIndexed { i, w ->
            if (!selected[i]) return@forEachIndexed
            if (sb.isNotEmpty()) sb.append(if (w.line != lastLine) '\n' else ' ')
            sb.append(w.text)
            lastLine = w.line
        }
        onChange?.invoke(sb.toString())
    }
}
