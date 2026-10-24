package com.matrixkey

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * The scanned photo with every recognised word outlined. Tap a word to add or
 * remove it; drag across words to select a run of them (like Gboard's text
 * scanner). [onChange] gets the selected words in reading order.
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

    /** [photoWidth] is the width the boxes were measured in (the recognised image). */
    fun set(bmp: Bitmap, photoWidth: Int, list: List<Word>) {
        bitmap = bmp
        words = list
        selected = BooleanArray(list.size) { true }
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
        val maxH = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        val h = if (b == null) 0 else minOf(maxH, (w * b.height / b.width.toFloat()).toInt())
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val scale = minOf(width / b.width.toFloat(), height / b.height.toFloat())
        val dw = b.width * scale; val dh = b.height * scale
        dst.set((width - dw) / 2, (height - dh) / 2, (width + dw) / 2, (height + dh) / 2)
        canvas.drawBitmap(b, null, dst, paint)
        canvas.drawRect(dst, shade)
        val r = RectF()
        words.forEachIndexed { i, w ->
            boxOnScreen(w, r)
            if (selected[i]) {
                fill.color = ColorUtils.setAlphaComponent(accent, 110)
                canvas.drawRoundRect(r, 4 * dp, 4 * dp, fill)
                stroke.color = accent
            } else {
                stroke.color = 0xAAFFFFFF.toInt()
            }
            canvas.drawRoundRect(r, 4 * dp, 4 * dp, stroke)
        }
    }

    private fun boxOnScreen(w: Word, out: RectF) {
        val b = bitmap ?: return
        val s = (dst.width() / b.width) * photoToBitmap
        out.set(dst.left + w.box.left * s, dst.top + w.box.top * s, dst.left + w.box.right * s, dst.top + w.box.bottom * s)
        out.inset(-2 * dp, -2 * dp)
    }

    private fun wordAt(x: Float, y: Float): Int {
        val r = RectF()
        var best = -1; var bestD = Float.MAX_VALUE
        words.forEachIndexed { i, w ->
            boxOnScreen(w, r)
            r.inset(-6 * dp, -6 * dp)
            if (r.contains(x, y)) {
                val d = kotlin.math.abs(r.centerX() - x) + kotlin.math.abs(r.centerY() - y)
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    private var anchor = -1
    private var dragged = false
    private var downX = 0f; private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anchor = wordAt(e.x, e.y)
                dragged = false
                downX = e.x; downY = e.y
                if (anchor < 0) return false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (anchor < 0) return false
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
            MotionEvent.ACTION_UP -> {
                if (anchor >= 0 && !dragged) {
                    selected[anchor] = !selected[anchor]
                    invalidate(); emit()
                }
                anchor = -1
            }
            MotionEvent.ACTION_CANCEL -> anchor = -1
        }
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
