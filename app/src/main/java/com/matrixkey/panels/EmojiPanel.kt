package com.matrixkey.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.matrixkey.engine.EmojiData
import com.matrixkey.keyboard.Icon
import com.matrixkey.keyboard.Icons
import android.content.SharedPreferences
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val ui: PanelUi,
    private val onEmoji: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onClose: () -> Unit,
    /** Opens the search line ("Search emoji") above the keys. */
    private val onSearchTap: () -> Unit = {},
    /** Results of a search, shown first; null for the normal panel. */
    private val results: List<String>? = null,
    private val query: String = "",
) : LinearLayout(context) {

    private val sp = PreferenceManager.getDefaultSharedPreferences(context)
    private val recents: MutableList<String> =
        sp.getString("recent_emoji", "")!!.split(" ").filter { it.isNotEmpty() }.toMutableList()

    private val scroll = ScrollView(context)
    private val grid = EmojiGrid(context)
    private val sectionTops = ArrayList<Int>()

    init {
        orientation = VERTICAL
        addView(searchPill(), LayoutParams(LayoutParams.MATCH_PARENT, ui.px(40f)).apply {
            setMargins(ui.px(8f), ui.px(4f), ui.px(8f), ui.px(2f))
        })
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottomBar(), LayoutParams(LayoutParams.MATCH_PARENT, ui.px(48f)))
        grid.setSections(buildSections())
    }

    private fun searchPill(): View {
        val row = ui.row()
        ui.pressable(row, ui.theme.key, 20f)
        row.setPadding(ui.px(14f), 0, ui.px(14f), 0)
        val icon = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                Icons.draw(canvas, Icon.SEARCH, width / 2f, height / 2f, height * 0.9f, ui.theme.sub, false)
            }
        }
        row.addView(icon, LayoutParams(ui.px(20f), ui.px(20f)))
        row.addView(ui.text(if (query.isNotBlank()) query else "Search emoji", 15f, if (query.isNotBlank()) ui.theme.text else ui.theme.sub).apply {
            setPadding(ui.px(10f), 0, 0, 0)
        })
        row.setOnClickListener { ui.haptic(it); onSearchTap() }
        return row
    }

    private fun buildSections(): List<Pair<String, List<String>>> {
        val s = ArrayList<Pair<String, List<String>>>()
        if (results != null) s += (if (results.isEmpty()) "No matches" else "Results") to results
        if (recents.isNotEmpty()) s += "Recent" to recents.toList()
        s += EmojiData.categories
        return s
    }

    private fun bottomBar(): View {
        val bar = ui.row()
        bar.setPadding(ui.px(6f), 0, ui.px(6f), ui.px(4f))
        val abc = ui.chip("ABC") { onClose() }
        bar.addView(abc, LayoutParams(LayoutParams.WRAP_CONTENT, ui.px(40f)))

        val tabsScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val tabs = ui.row()
        val sections = buildSections()
        sections.forEachIndexed { i, (name, list) ->
            val tab = TextView(context).apply {
                text = when { name == "Recent" -> "🕘"; name == "Results" || list.isEmpty() -> "🔍"; else -> list.first() }
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(ui.px(10f), 0, ui.px(10f), 0)
                contentDescription = name
                setOnClickListener { ui.haptic(it); scroll.smoothScrollTo(0, sectionTops.getOrElse(i) { 0 }) }
            }
            tabs.addView(tab, LayoutParams(LayoutParams.WRAP_CONTENT, ui.px(40f)))
        }
        tabsScroll.addView(tabs)
        bar.addView(tabsScroll, LayoutParams(0, ui.px(40f), 1f))

        val back = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                Icons.draw(canvas, Icon.BACKSPACE, width / 2f, height / 2f, height * 0.55f, ui.theme.text, false)
            }
        }
        ui.pressable(back, ui.theme.mod, 20f)
        back.contentDescription = "Backspace"
        back.setOnClickListener { ui.haptic(it); onBackspace() }
        bar.addView(back, LayoutParams(ui.px(56f), ui.px(40f)))
        return bar
    }

    companion object {
        private val TONES = listOf("\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF")

        /** The emoji without a variation selector, if it can take a skin tone ("👦", "✌"). */
        fun toneBase(e: String): String? {
            val plain = e.replace("\uFE0F", "")
            if (plain.isEmpty()) return null
            val cp = plain.codePointAt(0)
            if (Character.charCount(cp) != plain.length) return null
            return if (UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_MODIFIER_BASE)) plain else null
        }

        /** Default yellow, then light to dark skin tones. */
        fun toneOptions(e: String): List<String> = toneBase(e)?.let { b -> listOf(e) + TONES.map { b + it } } ?: listOf(e)

        private fun tones(sp: SharedPreferences): JSONObject = runCatching { JSONObject(sp.getString("emoji_tones", "{}")!!) }.getOrDefault(JSONObject())

        /** The skin tone you last picked for this emoji (or the emoji itself). */
        fun withTone(sp: SharedPreferences, e: String): String {
            val b = toneBase(e) ?: return e
            return tones(sp).optString(b, e)
        }

        fun saveTone(sp: SharedPreferences, e: String, chosen: String) {
            val b = toneBase(e) ?: return
            val t = tones(sp)
            if (chosen == e) t.remove(b) else t.put(b, chosen)
            sp.edit().putString("emoji_tones", t.toString()).apply()
        }
    }

    private fun picked(e: String) {
        ui.haptic(this)
        recents.remove(e)
        recents.add(0, e)
        while (recents.size > 32) recents.removeAt(recents.size - 1)
        sp.edit().putString("recent_emoji", recents.joinToString(" ")).apply()
        onEmoji(e)
    }

    /** Draws all emoji in one canvas for smooth scrolling. */
    private inner class EmojiGrid(context: Context) : View(context) {
        private var sections: List<Pair<String, List<String>>> = emptyList()
        private val cols = 8
        private val cell get() = width / cols.toFloat()
        private val headerH = ui.px(30f).toFloat()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        private val hl = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pressedIdx: Pair<Int, Int>? = null

        fun setSections(s: List<Pair<String, List<String>>>) { sections = s; requestLayout(); invalidate() }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val c = w / cols.toFloat()
            var h = 0f
            sectionTops.clear()
            for ((_, list) in sections) {
                sectionTops += h.toInt()
                h += headerH + ((list.size + cols - 1) / cols) * c
            }
            setMeasuredDimension(w, h.toInt() + ui.px(8f))
        }

        override fun onDraw(canvas: Canvas) {
            val c = cell
            var y = 0f
            paint.textSize = c * 0.6f
            header.textSize = ui.dp * 13f
            header.color = ui.theme.sub
            header.typeface = ui.dotTypeface
            sections.forEachIndexed { si, (name, list) ->
                canvas.drawText(name, ui.dp * 12f, y + headerH * 0.7f, header)
                y += headerH
                list.forEachIndexed { i, e ->
                    val col = i % cols; val row = i / cols
                    val cx = col * c + c / 2; val top = y + row * c
                    if (pressedIdx == si to i) {
                        hl.color = ColorUtils.setAlphaComponent(ui.theme.text, 30)
                        canvas.drawRoundRect(col * c + 3, top + 3, (col + 1) * c - 3, top + c - 3, c / 4, c / 4, hl)
                    }
                    val fm = paint.fontMetrics
                    canvas.drawText(withTone(sp, e), cx, top + c / 2 - (fm.ascent + fm.descent) / 2, paint)
                }
                y += ((list.size + cols - 1) / cols) * c
            }
            drawTonePopup(canvas)
        }

        // ---- Skin-tone picker (long-press) ----
        private var popupFor: Pair<Int, Int>? = null
        private var popupOptions: List<String> = emptyList()
        private val popupRect = android.graphics.RectF()
        private var popupHover = -1
        private val longPress = Handler(Looper.getMainLooper())

        private fun cellRect(p: Pair<Int, Int>): android.graphics.RectF {
            val c = cell
            var top = 0f
            for (si in 0 until p.first) top += headerH + ((sections[si].second.size + cols - 1) / cols) * c
            top += headerH + (p.second / cols) * c
            val left = (p.second % cols) * c
            return android.graphics.RectF(left, top, left + c, top + c)
        }

        private fun openTones(p: Pair<Int, Int>) {
            val e = sections[p.first].second[p.second]
            val opts = toneOptions(e)
            if (opts.size < 2) return
            popupFor = p; popupOptions = opts; popupHover = -1
            val c = cell
            val r = cellRect(p)
            val w = c * 0.95f * opts.size + ui.dp * 8
            val left = (r.centerX() - w / 2).coerceIn(ui.dp * 4, width - w - ui.dp * 4)
            val scrollTop = (parent as? View)?.scrollY?.toFloat() ?: 0f
            val above = r.top - c * 1.15f
            val top = if (above >= scrollTop) above else r.bottom + ui.dp * 4
            popupRect.set(left, top, left + w, top + c * 1.1f)
            parent?.requestDisallowInterceptTouchEvent(true)
            ui.haptic(this)
            pressedIdx = null
            invalidate()
        }

        private fun closeTones() { popupFor = null; popupHover = -1; invalidate() }

        private fun optionAt(x: Float, y: Float): Int {
            if (popupFor == null) return -1
            if (y < popupRect.top - cell || y > popupRect.bottom + cell) return -1
            val w = (popupRect.width() - ui.dp * 8) / popupOptions.size
            val i = ((x - popupRect.left - ui.dp * 4) / w).toInt()
            return if (i in popupOptions.indices) i else -1
        }

        private fun drawTonePopup(canvas: Canvas) {
            if (popupFor == null) return
            hl.color = ui.theme.key
            canvas.drawRoundRect(popupRect, ui.dp * 14, ui.dp * 14, hl)
            val w = (popupRect.width() - ui.dp * 8) / popupOptions.size
            val fm = paint.fontMetrics
            popupOptions.forEachIndexed { i, o ->
                val l = popupRect.left + ui.dp * 4 + i * w
                if (i == popupHover) {
                    hl.color = ColorUtils.setAlphaComponent(ui.theme.text, 40)
                    canvas.drawRoundRect(l + 2, popupRect.top + 4, l + w - 2, popupRect.bottom - 4, ui.dp * 10, ui.dp * 10, hl)
                }
                canvas.drawText(o, l + w / 2, popupRect.centerY() - (fm.ascent + fm.descent) / 2, paint)
            }
        }

        private fun chooseTone(i: Int) {
            val p = popupFor ?: return
            val e = sections[p.first].second[p.second]
            val chosen = popupOptions[i]
            saveTone(sp, e, chosen)
            closeTones()
            picked(chosen)
        }

        private fun hit(x: Float, y: Float): Pair<Int, Int>? {
            val c = cell
            var top = 0f
            sections.forEachIndexed { si, (_, list) ->
                val rows = (list.size + cols - 1) / cols
                val bottom = top + headerH + rows * c
                if (y in top..bottom) {
                    if (y < top + headerH) return null
                    val row = ((y - top - headerH) / c).toInt()
                    val col = (x / c).toInt().coerceIn(0, cols - 1)
                    val idx = row * cols + col
                    return if (idx < list.size) si to idx else null
                }
                top = bottom
            }
            return null
        }

        private var downY = 0f

        private var openedThisTouch = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    openedThisTouch = false
                    if (popupFor != null) {
                        // Picker already open: tap a tone, or anywhere else to close it.
                        val i = optionAt(e.x, e.y)
                        if (i >= 0 && popupRect.contains(e.x, e.y)) { popupHover = i; invalidate(); return true }
                        closeTones(); return true
                    }
                    downY = e.y; pressedIdx = hit(e.x, e.y); invalidate()
                    val p = pressedIdx
                    if (p != null && toneBase(sections[p.first].second[p.second]) != null) {
                        longPress.postDelayed({ if (pressedIdx == p) { openedThisTouch = true; openTones(p) } }, 380)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popupFor != null) {
                        val i = optionAt(e.x, e.y)
                        if (i != popupHover) { popupHover = i; invalidate() }
                    } else if (kotlin.math.abs(e.y - downY) > ui.dp * 8) {
                        longPress.removeCallbacksAndMessages(null)
                        pressedIdx = null; invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    longPress.removeCallbacksAndMessages(null)
                    if (popupFor != null) {
                        val i = if (popupHover >= 0) popupHover else -1
                        // Released on a tone: insert it. Released without sliding: keep the picker open to tap.
                        if (i >= 0) chooseTone(i) else if (!openedThisTouch) closeTones()
                        return true
                    }
                    val p = pressedIdx
                    pressedIdx = null
                    invalidate()
                    if (p != null && hit(e.x, e.y) == p) picked(withTone(sp, sections[p.first].second[p.second]))
                }
                MotionEvent.ACTION_CANCEL -> { longPress.removeCallbacksAndMessages(null); pressedIdx = null; invalidate() }
            }
            return true
        }
    }
}
