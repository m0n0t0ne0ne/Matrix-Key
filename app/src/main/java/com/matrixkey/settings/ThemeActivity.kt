package com.matrixkey.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import com.matrixkey.Prefs
import com.matrixkey.keyboard.Icon
import com.matrixkey.keyboard.KeyType
import com.matrixkey.keyboard.KeyboardView
import com.matrixkey.keyboard.Layouts
import com.matrixkey.keyboard.Mode
import com.matrixkey.keyboard.OverlayView
import com.matrixkey.theme.FontManager
import com.matrixkey.theme.KeyboardTheme
import com.matrixkey.theme.RboardImporter
import com.matrixkey.theme.ThemeManager
import java.io.File

/**
 * Theme screen: pick a theme, import Rboard themes, choose key shape, key
 * font and accent, with a live keyboard preview. Changes save on Apply.
 */
class ThemeActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()

    // Pending (unsaved) selections
    private var themeId = "dynamic"
    private var keyShape = "none"
    private var keyFont = "ndot"
    private var accent = ""

    private lateinit var myThemesRow: LinearLayout
    private lateinit var defaultsRow: LinearLayout
    private lateinit var previewHost: FrameLayout
    private lateinit var preview: KeyboardView
    private lateinit var previewOverlay: OverlayView
    private lateinit var shapeButton: Button
    private lateinit var fontRow: LinearLayout
    private lateinit var accentRow: LinearLayout
    private lateinit var applyButton: Button

    private val textColor get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val subColor get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
    private val primary get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
    private val outline get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.GRAY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        themeId = prefs.theme; keyShape = prefs.keyShape; keyFont = prefs.keyFont; accent = prefs.accent
        title = com.matrixkey.settings.dotTitle(this, "Theme")

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(8f), px(16f), px(16f))
        }
        scroll.addView(col)
        val (pageTop, _) = pageHeader(this, "Theme", showBack = true) { finish() }
        pageTop.setPadding(0, pageTop.paddingTop, 0, 0)
        col.addView(pageTop)

        col.addView(header("My themes"))
        myThemesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(myThemesRow) })

        col.addView(header("Default"))
        defaultsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(defaultsRow) })

        // Live preview
        previewHost = FrameLayout(this).apply {
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = 16 * dp; setColor(Color.BLACK) }
        }
        previewOverlay = OverlayView(this)
        preview = KeyboardView(this, previewOverlay)
        preview.listener = previewListener
        previewHost.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(56f)
        })
        previewHost.addView(previewOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.addView(previewHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(20f)
        })
        col.addView(TextView(this).apply {
            text = "Try typing or swiping on the preview."
            setTextColor(subColor); textSize = 12f
            setPadding(px(4f), px(6f), 0, 0)
        })

        // Key shape, key font and accent live in one rounded card.
        col.addView(header("Keys"))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18f), px(6f), px(18f), px(14f))
            background = GradientDrawable().apply {
                cornerRadius = 20 * dp
                setColor(androidx.core.content.ContextCompat.getColor(this@ThemeActivity, com.matrixkey.R.color.mk_card))
            }
        }
        col.addView(card)

        shapeButton = mkButton(this, false)
        shapeButton.setTextColor(textColor)
        shapeButton.setOnClickListener { showShapeMenu() }
        card.addView(settingRow("Key shape", shapeButton))

        fontRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(2f), 0, px(6f)) }
        card.addView(TextView(this).apply {
            text = "Key font"; setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(0, px(10f), 0, px(10f))
        })
        card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; addView(fontRow) })

        accentRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        card.addView(settingRow("Accent", accentRow))

        // Cancel / Apply
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(24f), 0, 0)
        }
        val cancel = mkButton(this, false).apply {
            text = "Cancel"
            setOnClickListener { finish() }
        }
        applyButton = mkButton(this, true).apply {
            text = "Apply"
            setOnClickListener { apply() }
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(12f) })
        buttons.addView(applyButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(buttons)

        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
        refreshAll()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ------------------------------------------------------------------

    private fun header(s: String) = TextView(this).apply {
        text = s.uppercase()
        typeface = FontManager.ndot(this@ThemeActivity)
        letterSpacing = 0.08f
        setTextColor(subColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(px(4f), px(22f), 0, px(10f))
    }

    private fun settingRow(label: String, control: View): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(64f)
        }
        row.addView(TextView(this).apply {
            text = label; setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(control)
        return row
    }

    private fun refreshAll() {
        buildTiles()
        buildFontRow()
        buildAccentRow()
        shapeButton.text = when (keyShape) { "rounded" -> "Rounded  ▾"; "rectangular" -> "Rectangular  ▾"; else -> "None  ▾" }
        updatePreview()
    }

    private fun buildTiles() {
        myThemesRow.removeAllViews()
        myThemesRow.addView(ThemeTile(this, null, "Import theme", selected = false, importTile = true).apply {
            setOnClickListener { pickTheme() }
        })
        val mine = ThemeManager.customThemes(this) + listOf(ThemeManager.BLACK, ThemeManager.MIDNIGHT)
        for (t in mine) {
            val tile = ThemeTile(this, t, t.name, selected = themeId == t.id)
            tile.setOnClickListener { themeId = t.id; refreshAll() }
            if (t.id.startsWith("custom:")) tile.setOnLongClickListener {
                AlertDialog.Builder(this).setTitle("Delete “${t.name}”?")
                    .setPositiveButton("Delete") { _, _ ->
                        ThemeManager.deleteCustom(this, t.id)
                        if (themeId == t.id) themeId = "dynamic"
                        if (prefs.theme == t.id) prefs.put("theme", "dynamic")
                        refreshAll()
                    }.setNegativeButton("Cancel", null).show()
                true
            }
            myThemesRow.addView(tile)
        }
        defaultsRow.removeAllViews()
        val defaults = listOf(
            "dynamic" to ThemeManager.dynamic(this),
            "system" to (if (ThemeManager.isNight(this)) ThemeManager.DARK else ThemeManager.LIGHT),
            "light" to ThemeManager.LIGHT,
            "dark" to ThemeManager.DARK,
        )
        val names = mapOf("dynamic" to "Dynamic Color", "system" to "System Auto", "light" to "Light", "dark" to "Dark")
        for ((id, t) in defaults) {
            val tile = ThemeTile(this, t, names.getValue(id), selected = themeId == id, split = id == "system")
            tile.setOnClickListener { themeId = id; refreshAll() }
            defaultsRow.addView(tile)
        }
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(if (selected) MaterialColors.getColor(this@ThemeActivity, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE) else textColor)
        // Vertical padding + wrap-content height so tall dot-matrix glyphs never clip.
        setPadding(px(16f), px(10f), px(16f), px(10f))
        includeFontPadding = true
        minWidth = px(64f)
        minHeight = px(44f)
        background = GradientDrawable().apply {
            cornerRadius = 22 * dp
            if (selected) setColor(primary) else { setColor(Color.TRANSPARENT); setStroke(px(1.5f), outline) }
        }
        setOnClickListener { onClick() }
    }

    private fun buildFontRow() {
        fontRow.removeAllViews()
        val opts = ArrayList<Pair<String, String>>()
        val nothing = FontManager.nothingFonts()
        // Every Nothing font found on the phone, each shown in its own typeface.
        nothing.forEach { opts += it.prefValue to it.label }
        if (nothing.isEmpty()) opts += "ndot" to "NDot (not found)"
        opts += "doto" to "Doto"
        opts += "system" to "System"
        val hasCustom = FontManager.customFontFile(this).exists()
        opts += "custom" to if (hasCustom) "Custom" else "Import…"
        // "ndot" (auto) counts as the best NDot match.
        val autoMatch = if (keyFont == "ndot") nothing.filter { it.isDot }
            .sortedWith(compareBy({ !it.file.name.contains("57") }, { it.file.name.length })).firstOrNull()?.prefValue else null
        opts.forEachIndexed { i, (id, label) ->
            val selected = keyFont == id || autoMatch == id
            val c = chip(label, selected) {
                if (id == "custom" && !FontManager.customFontFile(this).exists()) pickFont()
                else { keyFont = id; refreshAll() }
            }
            if (id != "custom") c.typeface = FontManager.keyTypeface(this, id)
            if (id == "custom" && hasCustom) c.setOnLongClickListener { pickFont(); true }
            fontRow.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = px(8f)
            })
        }
    }

    private fun buildAccentRow() {
        accentRow.removeAllViews()
        val options = listOf("" to "Auto", "#D71921" to "Red", "#13A89E" to "Teal", "#2F6FE4" to "Blue", "#E9A23B" to "Amber")
        for ((hex, name) in options) {
            val v = object : View(this) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    val r = width / 2f
                    if (accent == hex) {
                        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * dp; p.color = textColor
                        canvas.drawCircle(r, r, r - dp, p)
                    }
                    p.style = Paint.Style.FILL
                    if (hex.isEmpty()) {
                        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * dp; p.color = subColor
                        canvas.drawCircle(r, r, r - 6 * dp, p)
                        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = 10 * dp
                        canvas.drawText("Auto", r, r + 3.5f * dp, p)
                    } else {
                        p.color = Color.parseColor(hex)
                        canvas.drawCircle(r, r, r - 6 * dp, p)
                    }
                }
            }
            v.contentDescription = "$name accent"
            v.setOnClickListener { accent = hex; refreshAll() }
            accentRow.addView(v, LinearLayout.LayoutParams(px(44f), px(44f)).apply { marginStart = px(4f) })
        }
    }

    private fun showShapeMenu() {
        val m = PopupMenu(this, shapeButton)
        m.menu.add(0, 1, 0, "Rounded")
        m.menu.add(0, 2, 1, "Rectangular")
        m.menu.add(0, 3, 2, "None")
        m.setOnMenuItemClickListener {
            keyShape = when (it.itemId) { 1 -> "rounded"; 2 -> "rectangular"; else -> "none" }
            refreshAll(); true
        }
        m.show()
    }

    private fun updatePreview() {
        val t: KeyboardTheme = ThemeManager.resolve(this, prefs, themeId, accent)
        val tf = if (keyFont == "custom") FontManager.keyTypeface(this, "custom") else FontManager.keyTypeface(this, keyFont)
        val dot = FontManager.isDotFont(keyFont)
        (previewHost.background as GradientDrawable).setColor(t.bg)
        preview.theme = t
        preview.typeface = tf
        preview.dotStyle = dot
        preview.keyShape = keyShape
        preview.showHints = prefs.symbolHints
        preview.textScale = prefs.keyTextScale
        preview.dottedIcons = prefs.dottedIcons
        preview.popupEnabled = prefs.popup
        preview.glideEnabled = true
        preview.trailEnabled = true
        preview.feedback.hapticEnabled = prefs.haptic
        preview.feedback.strength = prefs.vibrationStrength
        preview.enterIcon = Icon.ENTER
        preview.mode = Mode.ALPHA
        preview.rows = Layouts.build(Mode.ALPHA, Layouts.Options(prefs.layout, prefs.numberRow, prefs.emojiKey))
        previewOverlay.trailColor = t.trail ?: t.accent
        previewOverlay.trailDotted = dot
        previewHost.findViewWithTag<View>("bar")?.let { previewHost.removeView(it) }
        previewHost.addView(PreviewBar(this, t, tf).apply { tag = "bar" }, 0,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56f)))
        preview.relayout()
        // Preview the background image of imported themes.
        val img = t.backgroundImage?.takeIf { File(it).exists() }
        previewHost.foreground = null
        if (img != null) {
            android.graphics.BitmapFactory.decodeFile(img)?.let { bmp ->
                previewHost.background = android.graphics.drawable.LayerDrawable(arrayOf(
                    GradientDrawable().apply { cornerRadius = 16 * dp; setColor(t.bg) },
                    android.graphics.drawable.BitmapDrawable(resources, bmp)
                ))
            }
        } else if (previewHost.background !is GradientDrawable) {
            previewHost.background = GradientDrawable().apply { cornerRadius = 16 * dp; setColor(t.bg) }
        }
    }

    private fun apply() {
        prefs.put("theme", themeId)
        prefs.put("key_shape", keyShape)
        prefs.put("key_font", keyFont)
        prefs.put("accent", accent)
        prefs.sp.edit().putLong("theme_applied_at", System.currentTimeMillis()).apply()
        applyButton.text = "Applied ✓"
        applyButton.postDelayed({ applyButton.text = "Apply" }, 1500)
    }

    // ------------------------------------------------------------------
    // Importing
    // ------------------------------------------------------------------

    private val THEME_REQ = 41
    private val FONT_REQ = 42

    private fun pickTheme() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, THEME_REQ)
    }

    private fun pickFont() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "font/*"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, FONT_REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            THEME_REQ -> try {
                val t = RboardImporter.import(this, uri)
                themeId = t.id
                Toast.makeText(this, "Imported “${t.name}”", Toast.LENGTH_SHORT).show()
                refreshAll()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Couldn't import this theme", Toast.LENGTH_LONG).show()
            }
            FONT_REQ -> try {
                val dest = FontManager.customFontFile(this)
                dest.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024); var total = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            total += n
                            if (total > 30L * 1024 * 1024) { out.close(); dest.delete(); throw Exception("That font file is too large") }
                            out.write(buf, 0, n)
                        }
                    }
                }
                val ok = runCatching { android.graphics.Typeface.createFromFile(dest) }.isSuccess
                if (!ok) { dest.delete(); throw Exception("That file isn't a font") }
                FontManager.clearCache()
                keyFont = "custom"
                refreshAll()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Couldn't import this font", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------
    // Preview helpers
    // ------------------------------------------------------------------

    private val previewListener = object : KeyboardView.Listener {
        override fun onChar(text: String, fromLongPress: Boolean) {}
        override fun onKey(type: KeyType) {}
        override fun onGlide(path: List<PointF>, times: List<Long>) {}
        override fun onCursorStep(forward: Boolean) = true
        override fun onDeleteSelect(words: Int) = true
        override fun onDeleteCommit() {}
        override fun onSpaceLongPress() {}
        override fun onKeysLaidOut(centers: Map<Char, PointF>, keyWidth: Float) {}
    }

    /** The suggestion strip drawn above the preview keys. */
    @SuppressLint("ViewConstructor")
    private class PreviewBar(context: Context, val t: KeyboardTheme, val tf: android.graphics.Typeface) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val h = height.toFloat()
            val btn = 40 * d
            p.color = t.chip
            canvas.drawCircle(8 * d + btn / 2, h / 2, btn / 2, p)
            canvas.drawCircle(width - 8 * d - btn / 2, h / 2, btn / 2, p)
            com.matrixkey.keyboard.Icons.draw(canvas, Icon.CHEVRON_RIGHT, 8 * d + btn / 2, h / 2, btn * 0.5f, t.text, false)
            com.matrixkey.keyboard.Icons.draw(canvas, Icon.CLIPBOARD, width - 8 * d - btn / 2, h / 2, btn * 0.5f, t.text, false)
            val l = 16 * d + btn; val r = width - 16 * d - btn
            val w = (r - l) / 3
            val words = listOf("and", "is", "in")
            p.typeface = tf
            p.textSize = h * 0.4f
            for (i in 0..2) {
                p.color = t.sub
                val cx = l + w * i + w / 2
                val fm = p.fontMetrics
                canvas.drawText(words[i], cx, h / 2 - (fm.ascent + fm.descent) / 2, p)
                if (i > 0) { p.color = t.divider; canvas.drawRect(l + w * i - d / 2, h * 0.3f, l + w * i + d / 2, h * 0.7f, p) }
            }
        }
    }

    /** A theme thumbnail tile with its name underneath. */
    @SuppressLint("ViewConstructor")
    private inner class ThemeTile(
        context: Context,
        val t: KeyboardTheme?,
        val label: String,
        val selected: Boolean,
        val importTile: Boolean = false,
        val split: Boolean = false,
    ) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val thumb = object : View(context) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    val r = RectF(3 * dp, 3 * dp, width - 3 * dp, height - 3 * dp)
                    val rad = 16 * dp
                    if (importTile) {
                        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * dp; p.color = outline
                        canvas.drawRoundRect(r, rad, rad, p)
                        p.strokeWidth = 3 * dp; p.color = primary; p.strokeCap = Paint.Cap.ROUND
                        val cx = width / 2f; val cy = height / 2f; val s = 14 * dp
                        canvas.drawLine(cx - s, cy, cx + s, cy, p); canvas.drawLine(cx, cy - s, cx, cy + s, p)
                        return
                    }
                    val th = t ?: return
                    p.style = Paint.Style.FILL
                    val path = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
                    canvas.save(); canvas.clipPath(path)
                    p.color = th.bg; canvas.drawRect(r, p)
                    if (split) { p.color = ThemeManager.LIGHT.bg; canvas.drawRect(r.left, r.top, r.centerX(), r.bottom, p) }
                    p.color = th.pill
                    canvas.drawRoundRect(r.left + r.width() * 0.22f, r.bottom - 22 * dp, r.right - r.width() * 0.34f, r.bottom - 13 * dp, 5 * dp, 5 * dp, p)
                    p.color = th.accent
                    canvas.drawRoundRect(r.right - 34 * dp, r.bottom - 24 * dp, r.right - 10 * dp, r.bottom - 11 * dp, 7 * dp, 7 * dp, p)
                    canvas.restore()
                    p.style = Paint.Style.STROKE; p.strokeWidth = 3 * dp
                    p.color = if (selected) primary else Color.TRANSPARENT
                    canvas.drawRoundRect(r, rad, rad, p)
                    if (selected) {
                        p.style = Paint.Style.FILL; p.color = primary
                        canvas.drawCircle(r.centerX(), r.top + 26 * dp, 16 * dp, p)
                        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * dp; p.strokeCap = Paint.Cap.ROUND
                        p.color = MaterialColors.getColor(this@ThemeActivity, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
                        val cx = r.centerX(); val cy = r.top + 26 * dp
                        canvas.drawLine(cx - 7 * dp, cy, cx - 2 * dp, cy + 5 * dp, p)
                        canvas.drawLine(cx - 2 * dp, cy + 5 * dp, cx + 8 * dp, cy - 5 * dp, p)
                    }
                }
            }
            addView(thumb, LayoutParams(px(112f), px(84f)))
            addView(TextView(context).apply {
                text = label
                setTextColor(subColor)
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
            }, LayoutParams(px(112f), ViewGroup.LayoutParams.WRAP_CONTENT))
            setPadding(0, 0, px(10f), 0)
            isClickable = true
            setBackgroundResource(android.R.color.transparent)
            contentDescription = label
        }
    }
}
