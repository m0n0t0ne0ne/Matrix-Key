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
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import androidx.core.graphics.ColorUtils
import com.matrixkey.theme.KeyboardTheme

/** The strip above the keys: suggestions, the toolbar, or a search / status line. */
@SuppressLint("ViewConstructor")
class TopBarView(context: Context) : View(context) {

    enum class Tool { EMOJI, GIF, CLIPBOARD, AI, SCAN, SETTINGS, MIC }
    enum class BarMode { SUGGESTIONS, TOOLBAR, SEARCH, MESSAGE }

    interface Listener {
        fun onSuggestion(index: Int)
        fun onSuggestionLongPress(index: Int)
        fun onToggleToolbar()
        fun onTool(tool: Tool)
        fun onSearchSubmit()
        fun onSearchClose()
        /** Tapped inside the search box: put the cursor before character [index]. */
        fun onSearchCursor(index: Int) {}
        fun onMessageClose()
        /** Close any open panel (emoji, GIFs, clipboard, AI) and show the keys. */
        fun onBackToKeyboard()
    }

    /** True while a panel replaces the keys: the left button then returns to the keyboard. */
    var panelOpen = false
        set(v) { field = v; invalidate() }
    /** The tool whose panel is open (drawn highlighted; tapping it again closes it). */
    var activeTool: Tool? = null
        set(v) { field = v; invalidate() }
    /** Called on every successful tap, for haptic feedback. */
    var onTapFeedback: (() -> Unit)? = null

    companion object { const val MAX_ITEMS = 12 }

    var listener: Listener? = null
    lateinit var theme: KeyboardTheme
    var typeface: Typeface = Typeface.DEFAULT
    var dotStyle = true

    var mode = BarMode.SUGGESTIONS
        set(v) { field = v; invalidate() }
    /**
     * Left, middle, right, then any extra suggestions (reached by scrolling the
     * strip). Text is always drawn at full size: long words widen their slot and
     * the strip scrolls instead of shrinking them.
     */
    var suggestions: List<String> = listOf("", "", "")
        set(v) {
            field = if (v.size >= 3) v.take(MAX_ITEMS) else (v + listOf("", "", "")).take(3)
            scrollPos = 0f
            scroller.forceFinished(true)
            invalidate()
        }
    /** Slot drawn emphasized (the word that space will insert). */
    var emphasized = -1
    var tools: List<Tool> = Tool.values().toList()
        set(v) { field = v; invalidate() }

    var searchText = ""
    /** Cursor position in [searchText]. */
    var searchCursor = 0
        set(v) { field = v; invalidate() }
    private var searchScroll = 0f
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
    // Scrollable suggestion strip: item i spans [itemLeft[i], itemRight[i]] in strip coordinates.
    private val strip = RectF()
    private var itemLeft = FloatArray(0)
    private var itemRight = FloatArray(0)
    private var contentWidth = 0f
    private var scrollPos = 0f
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null
    private var dragging = false
    private var downX = 0f
    private var lastX = 0f
    private var pressedItem = -1
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
        strip.set(leftBtn.right + 6 * dp, top, rightBtn.left - 6 * dp, top + btn)
        layoutStrip(btn)
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

    private fun suggestionTextSize(btn: Float) = btn * 0.46f

    private fun layoutStrip(btn: Float) {
        val items = suggestions
        val n = items.size
        text.typeface = typeface
        text.textSize = suggestionTextSize(btn)
        // Each item wants its text width plus padding. If everything fits, items share
        // the bar (equal slots when they can); otherwise they keep their natural
        // width (the first three at least a third each) and the strip scrolls.
        if (itemLeft.size != n) { itemLeft = FloatArray(n); itemRight = FloatArray(n) }
        val natural = FloatArray(n) { if (items[it].isEmpty()) 44 * dp else maxOf(44 * dp, text.measureText(items[it]) + 28 * dp) }
        val avail = strip.width()
        val equal = avail / n.coerceAtLeast(1)
        val sum = natural.sum()
        var x = 0f
        for (i in 0 until n) {
            val w = when {
                natural.all { it <= equal } -> equal
                sum <= avail -> natural[i] + (avail - sum) / n
                i < 3 -> maxOf(natural[i], avail / 3f)
                else -> natural[i]
            }
            itemLeft[i] = x; itemRight[i] = x + w
            x += w
        }
        contentWidth = x
        scrollPos = scrollPos.coerceIn(0f, maxScroll())
    }

    private fun maxScroll() = (contentWidth - strip.width()).coerceAtLeast(0f)

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPos = scroller.currX.toFloat().coerceIn(0f, maxScroll())
            postInvalidateOnAnimation()
        }
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
        Icons.draw(canvas, icon, r.centerX(), r.centerY(), r.height() * 0.6f, fg, false)
    }

    private fun drawSuggestions(canvas: Canvas) {
        val t = theme
        roundButton(canvas, leftBtn, Icon.CHEVRON_RIGHT, t.chip, t.text)
        roundButton(canvas, rightBtn, Icon.CLIPBOARD, t.chip, t.text)
        text.typeface = typeface
        text.textSize = suggestionTextSize(strip.height())
        val fm = text.fontMetrics
        canvas.save()
        canvas.clipRect(strip)
        val r = RectF()
        for (i in suggestions.indices) {
            r.set(strip.left + itemLeft[i] - scrollPos, strip.top, strip.left + itemRight[i] - scrollPos, strip.bottom)
            if (r.right < strip.left || r.left > strip.right) continue
            val s = suggestions[i]
            if (pressedItem == i && s.isNotEmpty()) {
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
            canvas.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text)
        }
        canvas.restore()
        // A soft fade at an edge shows there's more to scroll to.
        if (scrollPos < maxScroll() - 1) edgeFade(canvas, strip.right - 18 * dp, strip.right, t.bg, toRight = true)
        if (scrollPos > 1) edgeFade(canvas, strip.left, strip.left + 18 * dp, t.bg, toRight = false)
    }

    private fun edgeFade(canvas: Canvas, l: Float, r: Float, color: Int, toRight: Boolean) {
        val clear = ColorUtils.setAlphaComponent(color, 0)
        fill.shader = android.graphics.LinearGradient(l, 0f, r, 0f,
            if (toRight) clear else color, if (toRight) color else clear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(l, strip.top, r, strip.bottom, fill)
        fill.shader = null
    }

    private fun drawToolbar(canvas: Canvas) {
        val t = theme
        // Like Gboard: the tools grid sits on the left (tap it to go back to suggestions).
        roundButton(canvas, leftBtn, if (panelOpen) Icon.KEYBOARD else Icon.GRID, if (panelOpen) t.chip else null, t.text)
        tools.forEachIndexed { i, tool ->
            val icon = when (tool) {
                Tool.EMOJI -> Icon.EMOJI
                Tool.GIF -> Icon.GIF
                Tool.CLIPBOARD -> Icon.CLIPBOARD
                Tool.AI -> Icon.AI
                Tool.SCAN -> Icon.SCAN
                Tool.SETTINGS -> Icon.SETTINGS
                Tool.MIC -> Icon.MIC
            }
            val active = tool == activeTool
            roundButton(canvas, toolRects[i], icon, if (active) t.accent else if (tool == Tool.MIC) t.pill else null, if (active) t.onAccent else t.text)
        }
    }

    private fun drawSearch(canvas: Canvas) {
        val t = theme
        roundButton(canvas, leftBtn, Icon.KEYBOARD, t.chip, t.text)
        // The box is outlined in the accent color so it's clearly the field
        // being typed into (Android doesn't let a keyboard hide the app's own cursor).
        fill.color = t.key
        canvas.drawRoundRect(searchBox, searchBox.height() / 2, searchBox.height() / 2, fill)
        outline.color = t.accent
        outline.strokeWidth = 2 * dp
        val r = RectF(searchBox).apply { inset(dp, dp) }
        canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, outline)
        leftText.typeface = Typeface.DEFAULT
        leftText.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        val pad = 16 * dp
        val avail = searchBox.width() - pad * 2
        val fm = leftText.fontMetrics
        val baseline = searchBox.centerY() - (fm.ascent + fm.descent) / 2
        var cursorX = searchBox.left + pad
        if (searchText.isEmpty()) {
            searchScroll = 0f
            leftText.color = t.sub
            canvas.drawText(TextUtils.ellipsize(searchHint, leftText, avail, TextUtils.TruncateAt.END).toString(), searchBox.left + pad + 4 * dp, baseline, leftText)
        } else {
            leftText.color = t.text
            // Scroll the text so the cursor stays in view.
            val cur = searchCursor.coerceIn(0, searchText.length)
            val toCursor = leftText.measureText(searchText, 0, cur)
            val room = avail - 6 * dp
            if (toCursor - searchScroll > room) searchScroll = toCursor - room
            if (toCursor - searchScroll < 0) searchScroll = toCursor
            searchScroll = searchScroll.coerceAtLeast(0f)
            canvas.save()
            canvas.clipRect(searchBox.left + pad - 2 * dp, searchBox.top, searchBox.right - pad + 2 * dp, searchBox.bottom)
            canvas.drawText(searchText, searchBox.left + pad - searchScroll, baseline, leftText)
            canvas.restore()
            cursorX = searchBox.left + pad + toCursor - searchScroll
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
        leftText.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
        val l = leftBtn.right + 12 * dp
        val avail = width - l - 12 * dp
        val shown = TextUtils.ellipsize(message, leftText, avail, TextUtils.TruncateAt.START).toString()
        val fm = leftText.fontMetrics
        canvas.drawText(shown, l, height / 2f - (fm.ascent + fm.descent) / 2, leftText)
    }

    // ------------------------------------------------------------------

    private var downTime = 0L
    private val longPressMs = 450L

    private var lastUpX = 0f

    /** The character position in the search text closest to [x]. */
    private fun searchIndexAt(x: Float): Int {
        if (searchText.isEmpty()) return 0
        val rel = x - (searchBox.left + 16 * dp) + searchScroll
        var best = 0; var bestD = Float.MAX_VALUE
        for (i in 0..searchText.length) {
            val d = kotlin.math.abs(leftText.measureText(searchText, 0, i) - rel)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun hitTarget(x: Float, y: Float): Any? {
        if (leftBtn.contains(x, y)) return leftBtn
        when (mode) {
            BarMode.SUGGESTIONS -> {
                if (rightBtn.contains(x, y)) return rightBtn
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
            BarMode.SEARCH -> {
                if (rightBtn.contains(x, y)) return rightBtn
                if (searchBox.contains(x, y)) return searchBox
            }
            BarMode.MESSAGE -> {}
        }
        return null
    }

    private fun itemAt(x: Float): Int {
        val cx = x - strip.left + scrollPos
        for (i in suggestions.indices) if (cx >= itemLeft[i] && cx < itemRight[i]) return i
        return -1
    }

    /** Taps, long-presses and horizontal scrolling inside the suggestion strip. */
    private fun stripTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocity?.recycle(); velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                dragging = false
                downX = e.x; lastX = e.x
                pressedItem = itemAt(e.x)
                downTime = SystemClock.uptimeMillis()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                if (!dragging && kotlin.math.abs(e.x - downX) > 8 * dp && maxScroll() > 0f) {
                    dragging = true; pressedItem = -1
                }
                if (dragging) {
                    scrollPos = (scrollPos - (e.x - lastX)).coerceIn(0f, maxScroll())
                    invalidate()
                }
                lastX = e.x
            }
            MotionEvent.ACTION_UP -> {
                velocity?.addMovement(e)
                if (dragging) {
                    velocity?.computeCurrentVelocity(1000)
                    val vx = velocity?.xVelocity ?: 0f
                    scroller.fling(scrollPos.toInt(), 0, (-vx).toInt(), 0, 0, maxScroll().toInt(), 0, 0)
                    postInvalidateOnAnimation()
                } else {
                    val i = pressedItem
                    if (i >= 0 && itemAt(e.x) == i && suggestions[i].isNotEmpty()) {
                        onTapFeedback?.invoke()
                        val long = SystemClock.uptimeMillis() - downTime > longPressMs
                        if (long) listener?.onSuggestionLongPress(i) else listener?.onSuggestion(i)
                    }
                }
                pressedItem = -1
                velocity?.recycle(); velocity = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedItem = -1; dragging = false; invalidate() }
        }
        return true
    }

    private var stripGesture = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            layoutRects()
            stripGesture = mode == BarMode.SUGGESTIONS && strip.contains(e.x, e.y)
        }
        if (stripGesture) return stripTouch(e)
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
                lastUpX = e.x
                onTapFeedback?.invoke()
                click(target)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }

    private fun click(target: Any) {
        val l = listener ?: return
        when (mode) {
            BarMode.SUGGESTIONS -> when {
                target === leftBtn -> l.onToggleToolbar()
                target === rightBtn -> l.onTool(Tool.CLIPBOARD)
            }
            BarMode.TOOLBAR -> if (target === leftBtn) {
                if (panelOpen) l.onBackToKeyboard() else l.onToggleToolbar()
            } else {
                val i = toolRects.indexOfFirst { it === target }
                if (i >= 0) { if (panelOpen && tools[i] == activeTool) l.onBackToKeyboard() else l.onTool(tools[i]) }
            }
            BarMode.SEARCH -> when {
                target === leftBtn -> l.onSearchClose()
                target === searchBox -> l.onSearchCursor(searchIndexAt(lastUpX))
                else -> l.onSearchSubmit()
            }
            BarMode.MESSAGE -> if (target === leftBtn) l.onMessageClose()
        }
    }
}
