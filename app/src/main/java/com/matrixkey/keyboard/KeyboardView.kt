package com.matrixkey.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.matrixkey.theme.KeyboardTheme
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draws the keys and turns touches into taps, long-presses, glide paths,
 * spacebar cursor moves and swipe-to-delete.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val overlay: OverlayView) : View(context) {

    interface Listener {
        fun onChar(text: String, fromLongPress: Boolean = false)
        fun onKey(type: KeyType)
        fun onGlide(path: List<PointF>)
        /** Moves the cursor one step; returns true when it actually moved. */
        fun onCursorStep(forward: Boolean): Boolean
        /** Selects [words] words back from the cursor (swipe from backspace). Returns true when the selection changed. */
        fun onDeleteSelect(words: Int): Boolean
        fun onDeleteCommit()
        fun onSpaceLongPress()
        fun onKeysLaidOut(centers: Map<Char, PointF>, keyWidth: Float)
    }

    var listener: Listener? = null
    val feedback = Feedback(context)
    private val dp = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    // ---- Appearance ----
    var rows: List<Row> = emptyList()
        set(value) { field = value; requestLayout(); invalidate() }
    lateinit var theme: KeyboardTheme
    var typeface: Typeface = Typeface.DEFAULT
    var dotStyle = true
    /** Draw shift/backspace/enter/emoji as dot-matrix icons instead of solid ones. */
    var dottedIcons = false
    /** "rounded" | "rectangular" | "none" */
    var keyShape = "none"
    var showHints = true
    var popupEnabled = true
    var heightScale = 1f
    /** Letter size multiplier (Settings › Preferences › Key letter size). */
    var textScale = 1.2f
    var oneHanded = "off"
    var shift = ShiftState.OFF
        set(value) { field = value; invalidate() }
    var enterIcon: Icon = Icon.ENTER
        set(value) { field = value; invalidate() }
    var mode = Mode.ALPHA

    // ---- Behaviour ----
    var glideEnabled = true
    var trailEnabled = true
    var gestureCursor = true
    var gestureDelete = true
    var cursorHaptic = true
    var longPressDelay = 300L
    var learnAim = true

    private val boxed get() = keyShape != "none"
    private val allKeys get() = rows.flatMap { it.keys }
        .flatMap { if (it.type == KeyType.STACK) it.stack else listOf(it) }
        .filter { it.type != KeyType.SPACER }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }

    private var keyWidth = 1f

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun rowHeightPx(r: Row) = r.heightDp * dp * heightScale
    private val vPad get() = 4 * dp

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = rows.sumOf { rowHeightPx(it).toDouble() }.toFloat() + vPad * 2
        setMeasuredDimension(w, h.roundToInt())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val sidePad = 3 * dp
        var areaLeft = sidePad
        var areaRight = width - sidePad
        if (oneHanded != "off") {
            val narrow = width * 0.84f
            if (oneHanded == "right") areaLeft = width - narrow else areaRight = narrow
        }
        val gapX = 6 * dp
        val gapY = 8 * dp
        var y = vPad
        for (row in rows) {
            val h = rowHeightPx(row)
            val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (areaRight - areaLeft) / totalWeight
            var x = areaLeft
            for (k in row.keys) {
                val w = unit * k.weight
                k.hit.set(x, y, x + w, y + h)
                k.rect.set(x + gapX / 2, y + gapY / 2, x + w - gapX / 2, y + h - gapY / 2)
                x += w
            }
            y += h
        }
        // Stacked keys span several rows and split that height between their keys.
        rows.forEachIndexed { ri, row ->
            for (k in row.keys) {
                if (k.type != KeyType.STACK || k.stack.isEmpty()) continue
                val total = rows.drop(ri).take(k.rowSpan).sumOf { rowHeightPx(it).toDouble() }.toFloat()
                k.hit.bottom = k.hit.top + total
                val each = total / k.stack.size
                k.stack.forEachIndexed { i, c ->
                    val top = k.hit.top + i * each
                    c.hit.set(k.hit.left, top, k.hit.right, top + each)
                    c.rect.set(c.hit.left + gapX / 2, top + gapY / 4, c.hit.right - gapX / 2, top + each - gapY / 4)
                }
            }
        }
        keyWidth = rows.firstOrNull { r -> r.keys.any { it.isLetter } }?.keys?.firstOrNull { it.isLetter }?.hit?.width() ?: (width / 10f)
        if (mode == Mode.ALPHA) {
            val centers = HashMap<Char, PointF>()
            allKeys.filter { it.isLetter }.forEach { centers[it.label[0]] = PointF(it.hit.centerX(), it.hit.centerY()) }
            listener?.onKeysLaidOut(centers, keyWidth)
        }
    }

    fun relayout() {
        requestLayout()
        layoutKeys()
        invalidate()
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private fun cornerRadius(k: Key): Float = when {
        k.type == KeyType.ENTER -> k.rect.height() / 2
        !boxed && k.pill -> k.rect.height() / 2
        keyShape == "rectangular" -> 4 * dp
        else -> 11 * dp
    }

    override fun onDraw(canvas: Canvas) {
        if (!::theme.isInitialized) return
        val t = theme
        if (!boxed) drawDividers(canvas)
        for (k in allKeys) {
            val pressed = k === pressedKey
            var bg: Int? = when {
                k.type == KeyType.ENTER -> t.accent
                boxed -> if (k.mod) t.mod else t.key
                k.pill -> t.pill
                else -> null
            }
            if (pressed) bg = if (bg == null) ColorUtils.setAlphaComponent(t.text, 28) else ColorUtils.blendARGB(bg, t.text, 0.12f)
            if (bg != null) {
                fill.color = bg
                val r = cornerRadius(k)
                canvas.drawRoundRect(k.rect, r, r, fill)
            }
            drawContent(canvas, k)
        }
    }

    /** Thin column lines (number pad) when keys are borderless. */
    private fun drawDividers(canvas: Canvas) {
        fill.color = theme.divider
        val half = 0.5f * dp
        for (row in rows) for (k in row.keys) {
            if (k.type == KeyType.STACK) canvas.drawRect(k.hit.right - half, k.hit.top + 6 * dp, k.hit.right + half, k.hit.bottom - 6 * dp, fill)
            if (k.dividerLeft) canvas.drawRect(k.hit.left - half, k.hit.top, k.hit.left + half, k.hit.bottom, fill)
        }
    }

    private val spacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun drawContent(canvas: Canvas, k: Key) {
        val t = theme
        val fg = if (k.type == KeyType.ENTER) t.onAccent else t.text
        val cx = k.rect.centerX()
        val cy = k.rect.centerY()
        val iconSize = k.rect.height() * 0.6f
        when (k.type) {
            KeyType.SHIFT -> Icons.draw(
                canvas, if (shift == ShiftState.LOCKED) Icon.CAPS else Icon.SHIFT, cx, cy, iconSize, fg, dottedIcons,
                filledShift = shift == ShiftState.ON
            )
            KeyType.BACKSPACE -> Icons.draw(canvas, Icon.BACKSPACE, cx, cy, iconSize * 1.08f, fg, dottedIcons)
            KeyType.ENTER -> Icons.draw(canvas, enterIcon, cx, cy, iconSize * 1.05f, fg, dottedIcons)
            KeyType.EMOJI -> Icons.draw(canvas, Icon.EMOJI, cx, cy, iconSize, fg, dottedIcons)
            KeyType.SPACE -> if (k.label.isNotEmpty()) {
                // A small "␣" mark on labelled space keys (number pad).
                val w = k.rect.height() * 0.36f
                val h = w * 0.3f
                spacePaint.color = fg
                spacePaint.strokeWidth = 2.2f * dp
                val path = android.graphics.Path().apply {
                    moveTo(cx - w / 2, cy - h / 2); lineTo(cx - w / 2, cy + h / 2)
                    lineTo(cx + w / 2, cy + h / 2); lineTo(cx + w / 2, cy - h / 2)
                }
                canvas.drawPath(path, spacePaint)
            }
            KeyType.NUMPAD -> {
                // "12" over "34", like a little number pad.
                label.typeface = typeface
                label.color = fg
                label.textSize = k.rect.height() * 0.28f * (1f + (textScale - 1f) * 0.5f)
                val fm = label.fontMetrics
                val lh = (fm.descent - fm.ascent) * 0.92f
                val base = cy - (fm.ascent + fm.descent) / 2
                canvas.drawText("12", cx, base - lh / 2, label)
                canvas.drawText("34", cx, base + lh / 2, label)
            }
            else -> {
                var text = k.label
                if (k.isLetter && shift != ShiftState.OFF) text = text.uppercase()
                label.typeface = typeface
                label.color = fg
                label.textSize = when {
                    text.length > 2 -> k.rect.height() * 0.32f * (1f + (textScale - 1f) * 0.5f)
                    k.type == KeyType.CHAR && k.label in listOf(",", ".") -> k.rect.height() * 0.55f
                    else -> k.rect.height() * (if (dotStyle) 0.5f else 0.46f) * textScale
                }
                // Never wider than the key.
                val maxW = k.rect.width() * 0.86f
                val w = label.measureText(text)
                if (w > maxW) label.textSize *= maxW / w
                val fm = label.fontMetrics
                canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, label)
                if (showHints && k.hint != null && mode == Mode.ALPHA) {
                    hintPaint.typeface = typeface
                    hintPaint.color = t.sub
                    hintPaint.textSize = k.rect.height() * 0.24f * (1f + (textScale - 1f) * 0.4f)
                    canvas.drawText(k.hint, k.rect.right - 3 * dp, k.rect.top + hintPaint.textSize + 1 * dp, hintPaint)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Touch aim learning
    // ------------------------------------------------------------------

    /** Per-letter average offset of your taps from the key center, as a fraction of key size. */
    private val aim = HashMap<Char, PointF>()
    private val wordTaps = ArrayList<Triple<Char, Float, Float>>()

    fun loadAim(json: String?) {
        aim.clear()
        if (json.isNullOrEmpty()) return
        runCatching {
            val o = JSONObject(json)
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                aim[k[0]] = PointF(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
            }
        }
    }

    fun aimJson(): String {
        val o = JSONObject()
        aim.forEach { (c, p) -> o.put(c.toString(), org.json.JSONArray(listOf(p.x.toDouble(), p.y.toDouble()))) }
        return o.toString()
    }

    fun clearAim() { aim.clear(); wordTaps.clear() }

    /** The word just finished; [accepted] = it went in as typed (no correction). */
    fun commitAim(accepted: Boolean) {
        if (accepted && learnAim) {
            for ((c, dx, dy) in wordTaps) {
                val p = aim.getOrPut(c) { PointF() }
                p.x = (p.x * 0.94f + dx * 0.06f).coerceIn(-0.3f, 0.3f)
                p.y = (p.y * 0.94f + dy * 0.06f).coerceIn(-0.3f, 0.3f)
            }
        }
        wordTaps.clear()
    }

    private fun findKey(x: Float, y: Float): Key? {
        val keys = allKeys
        val naive = keys.firstOrNull { it.hit.contains(x, y) }
            ?: keys.minByOrNull { hypot(it.hit.centerX() - x, it.hit.centerY() - y) }
            ?: return null
        if (!naive.isLetter || !learnAim || aim.isEmpty()) return naive
        // Pick the letter whose learned "real" center is closest to the touch.
        var best = naive
        var bestD = Float.MAX_VALUE
        for (k in keys) {
            if (!k.isLetter) continue
            if (abs(k.hit.centerX() - x) > k.hit.width() * 1.2f || abs(k.hit.centerY() - y) > k.hit.height() * 1.2f) continue
            val off = aim[k.label[0]]
            val cx = k.hit.centerX() + (off?.x ?: 0f) * k.hit.width()
            val cy = k.hit.centerY() + (off?.y ?: 0f) * k.hit.height()
            val d = ((x - cx) / k.hit.width()).let { it * it } + ((y - cy) / k.hit.height()).let { it * it }
            if (d < bestD) { bestD = d; best = k }
        }
        return best
    }

    // ------------------------------------------------------------------
    // Touch handling
    // ------------------------------------------------------------------

    private enum class State { NONE, PRESS, OPTIONS, GLIDE, CURSOR, DELETE, REPEAT }

    private var state = State.NONE
    private var pressedKey: Key? = null
    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var anchorX = 0f
    private var cursorSteps = 0
    private var deleteWords = 0
    private val glidePath = ArrayList<PointF>()

    private val longPress = Runnable { onLongPress() }
    private val repeat = object : Runnable {
        override fun run() {
            if (state == State.REPEAT && pressedKey?.type == KeyType.BACKSPACE) {
                listener?.onKey(KeyType.BACKSPACE)
                feedback.tick(this@KeyboardView)
                handler.postDelayed(this, 55)
            }
        }
    }

    /** Converts a rect in this view to overlay coordinates. */
    private fun toOverlay(r: RectF): RectF {
        val a = IntArray(2); val b = IntArray(2)
        getLocationInWindow(a); overlay.getLocationInWindow(b)
        return RectF(r).apply { offset((a[0] - b[0]).toFloat(), (a[1] - b[1]).toFloat()) }
    }

    private fun toOverlayX(x: Float): Float {
        val a = IntArray(2); val b = IntArray(2)
        getLocationInWindow(a); overlay.getLocationInWindow(b)
        return x + (a[0] - b[0])
    }

    private fun toOverlayPoint(x: Float, y: Float): PointF {
        val a = IntArray(2); val b = IntArray(2)
        getLocationInWindow(a); overlay.getLocationInWindow(b)
        return PointF(x + (a[0] - b[0]), y + (a[1] - b[1]))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = e.getPointerId(0)
                down(e.x, e.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Fast typing: a second finger lands before the first lifts.
                if (state == State.PRESS) release(downX, downY)
                else if (state != State.NONE) return true
                val i = e.actionIndex
                pointerId = e.getPointerId(i)
                down(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) {
                    lastX = e.getX(i); lastY = e.getY(i)
                    if (state == State.GLIDE) {
                        for (h in 0 until e.historySize) move(e.getHistoricalX(i, h), e.getHistoricalY(i, h))
                    }
                    move(e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                if (e.getPointerId(i) == pointerId) {
                    release(e.getX(i), e.getY(i))
                    pointerId = -1
                }
            }
            MotionEvent.ACTION_UP -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) release(e.getX(i), e.getY(i)) else reset()
                pointerId = -1
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun down(x: Float, y: Float) {
        val k = findKey(x, y) ?: return
        pressedKey = k
        state = State.PRESS
        downX = x; downY = y; anchorX = x; lastX = x; lastY = y
        cursorSteps = 0; deleteWords = 0
        feedback.key(this, k.type)
        if (popupEnabled && k.type == KeyType.CHAR && k.label.isNotEmpty()) {
            val text = if (k.isLetter && shift != ShiftState.OFF) k.label.uppercase() else k.label
            overlay.typeface = typeface
            overlay.showPopup(toOverlay(k.rect), text, if (boxed) theme.key else theme.pill, theme.text)
        }
        handler.removeCallbacks(longPress)
        when {
            k.type == KeyType.BACKSPACE -> handler.postDelayed(longPress, 380)
            k.type == KeyType.SPACE || k.longPressOptions.isNotEmpty() -> handler.postDelayed(longPress, longPressDelay)
        }
        invalidate()
    }

    private fun move(x: Float, y: Float) {
        when (state) {
            State.PRESS -> {
                val k = pressedKey ?: return
                // Moving the finger cancels a pending long-press (so a glide that
                // starts slowly isn't hijacked by the symbol popup).
                if (hypot(x - downX, y - downY) > 10 * dp && k.type != KeyType.BACKSPACE) handler.removeCallbacks(longPress)
                val dx = x - downX; val dy = y - downY
                val dist = hypot(dx, dy)
                if (k.type == KeyType.SPACE && gestureCursor && abs(dx) > keyWidth * 0.45f) {
                    handler.removeCallbacks(longPress)
                    state = State.CURSOR
                    anchorX = x
                } else if (k.type == KeyType.BACKSPACE && gestureDelete && dx < -keyWidth * 0.5f) {
                    handler.removeCallbacks(longPress)
                    state = State.DELETE
                    anchorX = downX
                    move(x, y)
                } else if (k.isLetter && glideEnabled && mode == Mode.ALPHA && dist > keyWidth * 0.55f) {
                    handler.removeCallbacks(longPress)
                    overlay.hidePopup()
                    state = State.GLIDE
                    glidePath.clear()
                    glidePath += PointF(downX, downY)
                    glidePath += PointF(x, y)
                    if (trailEnabled) {
                        val p0 = toOverlayPoint(downX, downY); overlay.trailStart(p0.x, p0.y)
                        val p1 = toOverlayPoint(x, y); overlay.trailAdd(p1.x, p1.y)
                    }
                    pressedKey = null
                    invalidate()
                } else if (k.type == KeyType.CHAR && !k.hit.contains(x, y) && dist > keyWidth * 0.6f && !(k.isLetter && glideEnabled)) {
                    // Slid off a non-glide key: follow the finger.
                    findKey(x, y)?.let { nk ->
                        if (nk !== k && nk.type == KeyType.CHAR) {
                            handler.removeCallbacks(longPress)
                            pressedKey = nk; downX = x; downY = y
                            if (popupEnabled) overlay.showPopup(toOverlay(nk.rect), nk.label, if (boxed) theme.key else theme.pill, theme.text)
                            invalidate()
                        }
                    }
                }
            }
            State.CURSOR -> {
                // One step per ~11dp of travel; one haptic tick per character actually moved.
                val step = 11 * dp
                val target = ((x - anchorX) / step).toInt()
                while (cursorSteps != target) {
                    val fwd = target > cursorSteps
                    cursorSteps += if (fwd) 1 else -1
                    val moved = listener?.onCursorStep(fwd) == true
                    if (moved && cursorHaptic) feedback.tick(this)
                }
            }
            State.DELETE -> {
                val step = 20 * dp
                val words = ((anchorX - x) / step).toInt().coerceAtLeast(0)
                if (words != deleteWords) {
                    deleteWords = words
                    if (listener?.onDeleteSelect(words) == true) feedback.tick(this)
                }
            }
            State.OPTIONS -> overlay.selectOption(toOverlayX(x))
            State.GLIDE -> {
                val last = glidePath.last()
                if (hypot(x - last.x, y - last.y) > 2 * dp) {
                    glidePath += PointF(x, y)
                    if (trailEnabled) { val p = toOverlayPoint(x, y); overlay.trailAdd(p.x, p.y) }
                }
            }
            else -> {}
        }
    }

    private var lastX = 0f
    private var lastY = 0f

    private fun onLongPress() {
        val k = pressedKey ?: return
        if (state != State.PRESS) return
        if (k.type != KeyType.BACKSPACE && hypot(lastX - downX, lastY - downY) > 10 * dp) return
        when {
            k.type == KeyType.BACKSPACE -> {
                state = State.REPEAT
                listener?.onKey(KeyType.BACKSPACE)
                handler.postDelayed(repeat, 55)
            }
            k.type == KeyType.SPACE -> {
                listener?.onSpaceLongPress()
                reset()
            }
            k.longPressOptions.isNotEmpty() -> {
                val opts = k.longPressOptions.map { if (k.isLetter && shift != ShiftState.OFF) it.uppercase() else it }
                overlay.accent = theme.accent; overlay.onAccent = theme.onAccent
                overlay.typeface = typeface
                overlay.showOptions(toOverlay(k.rect), opts, 0, if (boxed) theme.key else theme.pill, theme.text)
                overlay.selectOption(toOverlayX(downX))
                state = State.OPTIONS
                feedback.tick(this)
            }
        }
    }

    private fun release(x: Float, y: Float) {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        val k = pressedKey
        when (state) {
            State.PRESS -> if (k != null) {
                when (k.type) {
                    KeyType.CHAR -> {
                        val text = if (k.isLetter && shift != ShiftState.OFF) k.label.uppercase() else k.label
                        if (k.isLetter) wordTaps += Triple(k.label[0], (downX - k.hit.centerX()) / k.hit.width(), (downY - k.hit.centerY()) / k.hit.height())
                        listener?.onChar(text)
                    }
                    else -> listener?.onKey(k.type)
                }
            }
            State.OPTIONS -> {
                val opts = overlay.options
                val idx = overlay.optionsSelected
                if (idx in opts.indices) listener?.onChar(opts[idx], fromLongPress = true)
                wordTaps.clear()
            }
            State.GLIDE -> {
                glidePath += PointF(x, y)
                listener?.onGlide(ArrayList(glidePath))
                wordTaps.clear()
            }
            State.DELETE -> if (deleteWords > 0) listener?.onDeleteCommit() else listener?.onDeleteSelect(0)
            else -> {}
        }
        reset()
    }

    private fun reset() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        if (state == State.GLIDE) overlay.trailEnd()
        state = State.NONE
        pressedKey = null
        overlay.hidePopup()
        overlay.hideOptions()
        invalidate()
    }

    fun clearWordTaps() = wordTaps.clear()
}
