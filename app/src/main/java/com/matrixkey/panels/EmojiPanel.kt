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
                    canvas.drawText(e, cx, top + c / 2 - (fm.ascent + fm.descent) / 2, paint)
                }
                y += ((list.size + cols - 1) / cols) * c
            }
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

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.y; pressedIdx = hit(e.x, e.y); invalidate() }
                MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(e.y - downY) > ui.dp * 8) { pressedIdx = null; invalidate() }
                MotionEvent.ACTION_UP -> {
                    val p = pressedIdx
                    pressedIdx = null
                    invalidate()
                    if (p != null && hit(e.x, e.y) == p) picked(sections[p.first].second[p.second])
                }
                MotionEvent.ACTION_CANCEL -> { pressedIdx = null; invalidate() }
            }
            return true
        }
    }
}
