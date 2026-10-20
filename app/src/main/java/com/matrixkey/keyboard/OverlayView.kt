package com.matrixkey.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/**
 * Drawn above everything: key-press popups, long-press option panels and the
 * glide trail. It never takes touches.
 */
class OverlayView(context: Context) : View(context) {
    private val dp = resources.displayMetrics.density

    init {
        isClickable = false
        isFocusable = false
    }

    // ---- Popup ----
    var popupKeyRect: RectF? = null
    var popupLabel: String = ""
    var popupBg = Color.WHITE
    var popupText = Color.BLACK
    var typeface: Typeface = Typeface.DEFAULT

    // ---- Long-press options ----
    var options: List<String> = emptyList()
    var optionsSelected = 0
    val optionCells = ArrayList<RectF>()
    var accent = Color.RED
    var onAccent = Color.WHITE

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- Trail ----
    private class TP(val x: Float, val y: Float, val t: Long)
    private val trail = ArrayList<TP>()
    var trailColor = Color.RED
    var trailDotted = true
    var trailWidthDp = 6f
    var trailActive = false
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fadeMs = 260L

    fun showPopup(rect: RectF, label: String, bg: Int, fg: Int) {
        popupKeyRect = RectF(rect); popupLabel = label; popupBg = bg; popupText = fg
        invalidate()
    }

    fun hidePopup() {
        if (popupKeyRect != null) { popupKeyRect = null; invalidate() }
    }

    /** Lays the option cells out above [rect], clamped to the screen width. */
    fun showOptions(rect: RectF, opts: List<String>, selected: Int, bg: Int, fg: Int) {
        options = opts; optionsSelected = selected; popupBg = bg; popupText = fg
        optionCells.clear()
        val cellW = max(rect.width(), 36 * dp)
        val cellH = rect.height() * 1.05f
        val total = cellW * opts.size
        var left = rect.centerX() - cellW / 2f
        if (left + total > width - 4 * dp) left = width - 4 * dp - total
        if (left < 4 * dp) left = 4 * dp
        val top = rect.top - cellH - 8 * dp
        for (i in opts.indices) optionCells += RectF(left + i * cellW, top, left + (i + 1) * cellW, top + cellH)
        popupKeyRect = null
        invalidate()
    }

    fun selectOption(x: Float): Int {
        if (optionCells.isEmpty()) return 0
        var idx = optionCells.indexOfFirst { x >= it.left && x < it.right }
        if (idx < 0) idx = if (x < optionCells.first().left) 0 else optionCells.size - 1
        if (idx != optionsSelected) { optionsSelected = idx; invalidate() }
        return idx
    }

    fun hideOptions() {
        if (options.isNotEmpty()) { options = emptyList(); optionCells.clear(); invalidate() }
    }

    fun trailStart(x: Float, y: Float) {
        trail.clear()
        trailActive = true
        trail += TP(x, y, SystemClock.uptimeMillis())
        postInvalidateOnAnimation()
    }

    fun trailAdd(x: Float, y: Float) {
        trail += TP(x, y, SystemClock.uptimeMillis())
        postInvalidateOnAnimation()
    }

    fun trailEnd() {
        trailActive = false
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        drawTrail(canvas)
        drawPopup(canvas)
        drawOptions(canvas)
    }

    private fun bubble(canvas: Canvas, r: RectF, color: Int) {
        shadow.color = Color.argb(60, 0, 0, 0)
        canvas.drawRoundRect(r.left, r.top + 2 * dp, r.right, r.bottom + 2 * dp, 12 * dp, 12 * dp, shadow)
        fill.color = color
        canvas.drawRoundRect(r, 12 * dp, 12 * dp, fill)
    }

    private fun drawPopup(canvas: Canvas) {
        val k = popupKeyRect ?: return
        val w = max(k.width() * 1.25f, 44 * dp)
        val h = k.height() * 1.25f
        val r = RectF(k.centerX() - w / 2, k.top - h - 6 * dp, k.centerX() + w / 2, k.top - 6 * dp)
        if (r.left < 2 * dp) r.offset(2 * dp - r.left, 0f)
        if (r.right > width - 2 * dp) r.offset(width - 2 * dp - r.right, 0f)
        bubble(canvas, r, popupBg)
        text.typeface = typeface
        text.color = popupText
        text.textSize = h * 0.52f
        val fm = text.fontMetrics
        canvas.drawText(popupLabel, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text)
    }

    private fun drawOptions(canvas: Canvas) {
        if (options.isEmpty() || optionCells.isEmpty()) return
        val all = RectF(optionCells.first().left - 4 * dp, optionCells.first().top - 4 * dp, optionCells.last().right + 4 * dp, optionCells.last().bottom + 4 * dp)
        bubble(canvas, all, popupBg)
        text.typeface = typeface
        options.forEachIndexed { i, s ->
            val c = optionCells[i]
            if (i == optionsSelected) {
                fill.color = accent
                canvas.drawRoundRect(c.left + 2 * dp, c.top + 2 * dp, c.right - 2 * dp, c.bottom - 2 * dp, 9 * dp, 9 * dp, fill)
            }
            text.color = if (i == optionsSelected) onAccent else popupText
            text.textSize = c.height() * 0.45f
            val fm = text.fontMetrics
            canvas.drawText(s, c.centerX(), c.centerY() - (fm.ascent + fm.descent) / 2, text)
        }
    }

    private fun drawTrail(canvas: Canvas) {
        if (trail.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (!trailActive) {
            // Fade out after the finger lifts.
            val last = trail.last().t
            if (now - last > fadeMs) { trail.clear(); return }
        }
        // Drop points that have aged out of the visible tail.
        val maxAge = 420L
        val cutoff = now - maxAge
        while (trail.size > 2 && trail[0].t < cutoff && trailActive) trail.removeAt(0)

        val base = trailWidthDp * dp
        val endFade = if (trailActive) 1f else (1f - (now - trail.last().t).toFloat() / fadeMs).coerceIn(0f, 1f)
        val total = trail.size

        if (trailDotted) {
            // Dots evenly spaced along the path, growing and brightening toward the finger.
            val spacing = base * 1.7f
            fill.style = Paint.Style.FILL
            var carry = 0f
            for (i in 1 until total) {
                val a = trail[i - 1]; val b = trail[i]
                val seg = hypot(b.x - a.x, b.y - a.y)
                var d = spacing - carry
                while (d <= seg) {
                    val t = d / seg
                    val x = a.x + (b.x - a.x) * t
                    val y = a.y + (b.y - a.y) * t
                    val prog = (i - 1 + t) / max(1, total - 1)
                    val alpha = (0.15f + 0.85f * prog.pow(1.3f)) * endFade
                    val r = base * (0.55f + 0.45f * prog) / 2f
                    fill.color = ColorUtils.setAlphaComponent(trailColor, (alpha * 255).toInt().coerceIn(0, 255))
                    canvas.drawCircle(x, y, r, fill)
                    d += spacing
                }
                carry = seg - (d - spacing)
            }
        } else {
            for (i in 1 until total) {
                val a = trail[i - 1]; val b = trail[i]
                val prog = i.toFloat() / max(1, total - 1)
                trailPaint.strokeWidth = base * (0.35f + 0.65f * prog)
                trailPaint.color = ColorUtils.setAlphaComponent(trailColor, ((0.1f + 0.9f * prog) * endFade * 255).toInt().coerceIn(0, 255))
                canvas.drawLine(a.x, a.y, b.x, b.y, trailPaint)
            }
        }
        if (trail.isNotEmpty()) postInvalidateOnAnimation()
    }
}
