package com.matrixkey.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.matrixkey.theme.KeyboardTheme

/** The strip above the keys: suggestions, the toolbar, or a search / status line. */
@SuppressLint("ViewConstructor")
class TopBarView(context: Context) : View(context) {

    enum class Tool { EMOJI, GIF, CLIPBOARD, AI, SETTINGS, MIC }
    enum class BarMode { SUGGESTIONS, TOOLBAR, SEARCH, MESSAGE }

    interface Listener {
        fun onSuggestion(index: Int)
        fun onSuggestionLongPress(index: Int)
        fun onToggleToolbar()
        fun onTool(tool: Tool)
        fun onSearchSubmit()
        fun onSearchClose()
        fun onMessageClose()
    }

    var listener: Listener? = null
    lateinit var theme: KeyboardTheme
    var typeface: Typeface = Typeface.DEFAULT
    var dotStyle = true

    var mode = BarMode.SUGGESTIONS
        set(v) { field = v; invalidate() }
    /** Three slots: left, middle, right. Empty strings are blank slots. */
    var suggestions: List<String> = listOf("", "", "")
        set(v) { field = (v + listOf("", "", "")).take(3); invalidate() }
    /** Slot drawn emphasized (the word that space will insert). */
    var emphasized = -1
    var tools: List<Tool> = Tool.values().toList()
        set(v) { field = v; invalidate() }

    var searchText = ""
        set(v) { field = v; invalidate() }
    var searchHint = "Search"
    var searchIcon: Icon = Icon.SEARCH
    var message = ""
        set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val leftText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }

    private val leftBtn = RectF()
    private val rightBtn = RectF()
    private val slots = Array(3) { RectF() }
    private val toolRects = ArrayList<RectF>()
    private val searchBox = RectF()
    private var pressed: Any? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (52 * dp).toInt())
    }

    private fun layoutRects() {
        val h = height.toFloat()
        val btn = 44 * dp
        val top = (h - btn) / 2
        leftBtn.set(6 * dp, top, 6 * dp + btn, top + btn)
        rightBtn.set(width - 6 * dp - btn, top, width - 6 * dp, top + btn)
        val l = leftBtn.right + 6 * dp
        val r = rightBtn.left - 6 * dp
        val w = (r - l) / 3
        for (i in 0..2) slots[i].set(l + i * w, top, l + (i + 1) * w, top + btn)
        val n = tools.size
        while (toolRects.size < n) toolRects += RectF()
        while (toolRects.size > n) toolRects.removeAt(toolRects.size - 1)
        val area = width - leftBtn.right - 8 * dp
        val cell = area / n.coerceAtLeast(1)
        for (i in 0 until n) {
            val cx = leftBtn.right + 4 * dp + cell * i + cell / 2
            toolRects[i].set(cx - btn / 2, top, cx + btn / 2, top + btn)
        }
        searchBox.set(leftBtn.right + 6 * dp, top, rightBtn.left - 6 * dp, top + btn)
    }

    override fun onDraw(canvas: Canvas) {
        if (!::theme.isInitialized) return
        layoutRects()
        when (mode) {
            BarMode.SUGGESTIONS -> drawSuggestions(canvas)
            BarMode.TOOLBAR -> drawToolbar(canvas)
            BarMode.SEARCH -> drawSearch(canvas)
            BarMode.MESSAGE -> drawMessage(canvas)
        }
    }

    private fun roundButton(canvas: Canvas, r: RectF, icon: Icon, bg: Int?, fg: Int) {
        if (bg != null) {
            fill.color = if (pressed === r) ColorUtils.blendARGB(bg, theme.text, 0.12f) else bg
            canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
        } else if (pressed === r) {
            fill.color = ColorUtils.setAlphaComponent(theme.text, 28)
            canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
        }
        Icons.draw(canvas, icon, r.centerX(), r.centerY(), r.height() * 0.5f, fg, false)
    }

    private fun drawSuggestions(canvas: Canvas) {
        val t = theme
        roundButton(canvas, leftBtn, Icon.CHEVRON_RIGHT, t.chip, t.text)
        roundButton(canvas, rightBtn, Icon.CLIPBOARD, t.chip, t.text)
        text.typeface = typeface
        for (i in 0..2) {
            val s = suggestions[i]
            val r = slots[i]
            if (pressed === r && s.isNotEmpty()) {
                fill.color = ColorUtils.setAlphaComponent(t.text, 24)
                canvas.drawRoundRect(r, 10 * dp, 10 * dp, fill)
            }
            if (i > 0) {
                fill.color = t.divider
                canvas.drawRect(r.left - 0.5f * dp, r.top + 10 * dp, r.left + 0.5f * dp, r.bottom - 10 * dp, fill)
            }
            if (s.isEmpty()) continue
            text.color = if (i == emphasized) t.text else t.sub
            text.isFakeBoldText = i == emphasized && !dotStyle
            text.textSize = r.height() * 0.46f
            val avail = r.width() - 8 * dp
            while (text.measureText(s) > avail && text.textSize > r.height() * 0.28f) text.textSize *= 0.92f
            val shown = TextUtils.ellipsize(s, text, avail, TextUtils.TruncateAt.END).toString()
            val fm = text.fontMetrics
            canvas.drawText(shown, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text)
        }
    }

    private fun drawToolbar(canvas: Canvas) {
        val t = theme
        roundButton(canvas, leftBtn, Icon.CHEVRON_LEFT, t.chip, t.text)
        tools.forEachIndexed { i, tool ->
            val icon = when (tool) {
                Tool.EMOJI -> Icon.EMOJI
                Tool.GIF -> Icon.GIF
                Tool.CLIPBOARD -> Icon.CLIPBOARD
                Tool.AI -> Icon.AI
                Tool.SETTINGS -> Icon.SETTINGS
                Tool.MIC -> Icon.MIC
            }
            roundButton(canvas, toolRects[i], icon, if (tool == Tool.MIC) t.pill else null, t.text)
        }
    }

    private fun drawSearch(canvas: Canvas) {
        val t = theme
        roundButton(canvas, leftBtn, Icon.CLOSE, t.chip, t.text)
        // The box is outlined in the accent color so it's clearly the field
        // being typed into (Android doesn't let a keyboard hide the app's own cursor).
        fill.color = t.key
        canvas.drawRoundRect(searchBox, searchBox.height() / 2, searchBox.height() / 2, fill)
        outline.color = t.accent
        outline.strokeWidth = 2 * dp
        val r = RectF(searchBox).apply { inset(dp, dp) }
        canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, outline)
        leftText.typeface = Typeface.DEFAULT
        leftText.textSize = 16 * resources.displayMetrics.scaledDensity
        val pad = 16 * dp
        val avail = searchBox.width() - pad * 2
        val fm = leftText.fontMetrics
        val baseline = searchBox.centerY() - (fm.ascent + fm.descent) / 2
        var cursorX = searchBox.left + pad
        if (searchText.isEmpty()) {
            leftText.color = t.sub
            canvas.drawText(TextUtils.ellipsize(searchHint, leftText, avail, TextUtils.TruncateAt.END).toString(), searchBox.left + pad + 4 * dp, baseline, leftText)
        } else {
            leftText.color = t.text
            // Keep the end of the text (where you're typing) visible.
            var shown = searchText
            while (leftText.measureText(shown) > avail - 4 * dp && shown.length > 1) shown = shown.substring(1)
            canvas.drawText(shown, searchBox.left + pad, baseline, leftText)
            cursorX += leftText.measureText(shown) + 2 * dp
        }
        if ((SystemClock.uptimeMillis() / 530) % 2 == 0L) {
            fill.color = t.accent
            canvas.drawRect(cursorX, searchBox.top + 12 * dp, cursorX + 2 * dp, searchBox.bottom - 12 * dp, fill)
        }
        postInvalidateDelayed(530)
        roundButton(canvas, rightBtn, searchIcon, t.accent, t.onAccent)
    }

    private fun drawMessage(canvas: Canvas) {
        val t = theme
        roundButton(canvas, leftBtn, Icon.CLOSE, t.chip, t.text)
        leftText.typeface = Typeface.DEFAULT
        leftText.color = t.text
        leftText.textSize = 15 * resources.displayMetrics.scaledDensity
        val l = leftBtn.right + 12 * dp
        val avail = width - l - 12 * dp
        val shown = TextUtils.ellipsize(message, leftText, avail, TextUtils.TruncateAt.START).toString()
        val fm = leftText.fontMetrics
        canvas.drawText(shown, l, height / 2f - (fm.ascent + fm.descent) / 2, leftText)
    }

    // ------------------------------------------------------------------

    private var downTime = 0L
    private val longPressMs = 450L

    private fun hitTarget(x: Float, y: Float): Any? {
        if (leftBtn.contains(x, y)) return leftBtn
        when (mode) {
            BarMode.SUGGESTIONS -> {
                if (rightBtn.contains(x, y)) return rightBtn
                slots.forEach { if (it.contains(x, y)) return it }
            }
            BarMode.TOOLBAR -> {
                // Whole-cell hit areas, so taps between icons still count.
                val n = toolRects.size
                if (n > 0 && x > leftBtn.right) {
                    val cell = (width - leftBtn.right - 8 * dp) / n
                    val i = ((x - leftBtn.right - 4 * dp) / cell).toInt().coerceIn(0, n - 1)
                    return toolRects[i]
                }
            }
            BarMode.SEARCH -> if (rightBtn.contains(x, y)) return rightBtn
            BarMode.MESSAGE -> {}
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layoutRects()
                pressed = hitTarget(e.x, e.y)
                downTime = SystemClock.uptimeMillis()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val target = pressed
                pressed = null
                invalidate()
                if (target == null || hitTarget(e.x, e.y) !== target) return true
                val long = SystemClock.uptimeMillis() - downTime > longPressMs
                click(target, long)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }

    private fun click(target: Any, long: Boolean) {
        val l = listener ?: return
        when (mode) {
            BarMode.SUGGESTIONS -> when {
                target === leftBtn -> l.onToggleToolbar()
                target === rightBtn -> l.onTool(Tool.CLIPBOARD)
                else -> {
                    val i = slots.indexOfFirst { it === target }
                    if (i >= 0 && suggestions[i].isNotEmpty()) if (long) l.onSuggestionLongPress(i) else l.onSuggestion(i)
                }
            }
            BarMode.TOOLBAR -> if (target === leftBtn) l.onToggleToolbar() else {
                val i = toolRects.indexOfFirst { it === target }
                if (i >= 0) l.onTool(tools[i])
            }
            BarMode.SEARCH -> if (target === leftBtn) l.onSearchClose() else l.onSearchSubmit()
            BarMode.MESSAGE -> if (target === leftBtn) l.onMessageClose()
        }
    }
}
