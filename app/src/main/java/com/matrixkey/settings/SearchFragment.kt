package com.matrixkey.settings

import android.content.Context
import android.content.res.XmlResourceParser
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.matrixkey.R

/** Searches every setting (titles and descriptions) across all screens. */
class SearchFragment : Fragment() {

    data class Entry(val xml: String, val screen: String, val title: String, val summary: String, val key: String?)

    private lateinit var results: LinearLayout
    private val index by lazy { buildIndex(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val field = EditText(ctx).apply {
            hint = "Search settings"
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(ContextCompat.getColor(ctx, R.color.mk_text))
            setHintTextColor(ContextCompat.getColor(ctx, R.color.mk_sub))
            setPadding((22 * d).toInt(), 0, (22 * d).toInt(), 0)
            background = GradientDrawable().apply { cornerRadius = 30 * d; setColor(ContextCompat.getColor(ctx, R.color.mk_card)) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mk_search, 0, 0, 0)
            compoundDrawablePadding = (14 * d).toInt()
        }
        col.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * d).toInt()).apply {
            setMargins((16 * d).toInt(), 0, (16 * d).toInt(), (12 * d).toInt())
        })
        results = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (32 * d).toInt()) }
        col.addView(ScrollView(ctx).apply { addView(results) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = show(s?.toString().orEmpty())
        })
        field.requestFocus()
        field.post {
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
        show("")
        return col
    }

    private fun show(q: String) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        results.removeAllViews()
        val query = q.trim().lowercase()
        if (query.isEmpty()) {
            results.addView(note(ctx, "Type to find any setting, like “vibration” or “trail”."))
            return
        }
        val words = query.split(Regex("\\s+"))
        val hits = index.filter { e ->
            val hay = (e.title + " " + e.summary + " " + e.screen).lowercase()
            words.all { hay.contains(it) }
        }.sortedBy { if (it.title.lowercase().contains(query)) 0 else 1 }
        if (hits.isEmpty()) { results.addView(note(ctx, "No settings match “$q”")); return }
        hits.forEachIndexed { i, e ->
            val first = i == 0; val last = i == hits.size - 1
            val big = 26 * d; val small = 6 * d
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * d).toInt(), (14 * d).toInt(), (20 * d).toInt(), (14 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(ctx, R.color.mk_card))
                    val t = if (first) big else small; val b = if (last) big else small
                    cornerRadii = floatArrayOf(t, t, t, t, b, b, b, b)
                }
                isClickable = true
                setOnClickListener { (activity as? SettingsActivity)?.openScreen(e.xml, e.screen, e.key) }
            }
            row.addView(TextView(ctx).apply {
                text = e.title
                setTextColor(ContextCompat.getColor(ctx, R.color.mk_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            })
            row.addView(TextView(ctx).apply {
                text = if (e.summary.isNotBlank()) "${e.screen} › ${e.summary}" else e.screen
                setTextColor(ContextCompat.getColor(ctx, R.color.mk_sub))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 2
            })
            results.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (3 * d).toInt()
            })
        }
    }

    private fun note(ctx: Context, s: String) = TextView(ctx).apply {
        text = s
        setTextColor(ContextCompat.getColor(ctx, R.color.mk_sub))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding((8 * resources.displayMetrics.density).toInt(), (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    companion object {
        private val SCREENS = listOf(
            "prefs_main" to "Settings", "prefs_languages" to "Languages", "prefs_preferences" to "Preferences",
            "prefs_text" to "Text correction", "prefs_glide" to "Glide typing", "prefs_voice" to "Voice typing",
            "prefs_ai" to "AI writing", "prefs_gifs" to "GIFs", "prefs_clipboard" to "Clipboard", "prefs_privacy" to "Privacy",
        )

        private const val NS = "http://schemas.android.com/apk/res/android"

        fun buildIndex(ctx: Context): List<Entry> {
            val out = ArrayList<Entry>()
            for ((xml, screen) in SCREENS) {
                val id = ctx.resources.getIdentifier(xml, "xml", ctx.packageName)
                if (id == 0) continue
                val p: XmlResourceParser = ctx.resources.getXml(id)
                runCatching {
                    while (p.next() != XmlResourceParser.END_DOCUMENT) {
                        if (p.eventType != XmlResourceParser.START_TAG) continue
                        if (p.name.endsWith("DotCategory") || p.name == "PreferenceScreen" || p.name == "extra" || p.name == "intent") continue
                        val title = attr(ctx, p, "title") ?: continue
                        val summary = attr(ctx, p, "summary").orEmpty()
                        // Main-screen tiles point at their own screen.
                        if (xml == "prefs_main") {
                            val target = SCREENS.firstOrNull { it.second == title }?.first
                                ?: when (title) { "Theme" -> "theme"; "Dictionary" -> "dictionary"; else -> "prefs_main" }
                            out += Entry(target, title, title, summary, attr(ctx, p, "key"))
                        } else {
                            out += Entry(xml, screen, title, summary, attr(ctx, p, "key"))
                        }
                    }
                }
                p.close()
            }
            // Settings that live on the custom Theme and Dictionary screens.
            listOf(
                "Key shape" to "Rounded, rectangular or none (floating keys)",
                "Key font" to "NDot, Nothing fonts, Doto, system or your own font",
                "Accent color" to "Enter key and glide trail color",
                "Import Rboard theme" to "Use a Gboard .pack theme",
                "Dynamic color" to "Match your wallpaper",
                "Light and dark" to "System auto, light, dark, pure black, midnight",
            ).forEach { (t, s) -> out += Entry("theme", "Theme", t, s, null) }
            listOf(
                "Personal dictionary" to "Add your own words",
                "Learned words" to "Words picked up from your typing",
                "Export or import words" to "Back up your dictionary",
            ).forEach { (t, s) -> out += Entry("dictionary", "Dictionary", t, s, null) }
            return out
        }

        private fun attr(ctx: Context, p: XmlResourceParser, name: String): String? {
            val res = p.getAttributeResourceValue(NS, name, 0)
            if (res != 0) return runCatching { ctx.getString(res) }.getOrNull()
            return p.getAttributeValue(NS, name)
        }
    }
}
