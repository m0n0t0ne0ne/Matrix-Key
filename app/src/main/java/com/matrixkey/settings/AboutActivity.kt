package com.matrixkey.settings

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.matrixkey.R
import com.matrixkey.theme.FontManager

/** Settings › About: version, privacy in brief, credits and links. */
class AboutActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()
    private val textColor get() = ContextCompat.getColor(this, R.color.mk_text)
    private val subColor get() = ContextCompat.getColor(this, R.color.mk_sub)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = dotTitle(this, "About")
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(8f), px(16f), px(32f))
        }
        val scroll = ScrollView(this).apply { addView(col) }
        val (header, _) = pageHeader(this, "About", showBack = true) { finish() }
        header.setPadding(0, header.paddingTop, 0, 0)
        col.addView(header)

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        val fonts = FontManager.nothingFonts().joinToString { it.label }.ifEmpty { "None found, using Doto" }

        col.addView(card(
            row("Version", version),
            row("Nothing fonts", fonts),
        ))
        col.addView(sectionTitle("Privacy"))
        col.addView(card(note(
            "Typing, learning, your dictionary and clipboard stay on this phone. " +
                "Only GIF search and AI writing use the internet, and only when you use them. " +
                "Scan text reads images on the phone."
        )))
        col.addView(sectionTitle("Credits"))
        col.addView(card(
            row("Doto font", "SIL Open Font License 1.1"),
            row("Word list", "FrequencyWords, CC BY-SA 4.0"),
            row("Text recognition", "Google ML Kit"),
            row("Android libraries", "Apache License 2.0"),
        ))
        col.addView(sectionTitle("Links"))
        col.addView(card(
            link("Source code", "github.com/m0n0t0ne0ne/Matrix-Key", "https://github.com/m0n0t0ne0ne/Matrix-Key"),
            link("Report a problem", "Open an issue on GitHub", "https://github.com/m0n0t0ne0ne/Matrix-Key/issues"),
            link("Licence", "MIT", "https://github.com/m0n0t0ne0ne/Matrix-Key/blob/main/LICENSE"),
        ))
        col.addView(TextView(this).apply {
            text = "Unofficial app, not affiliated with Nothing."
            setTextColor(subColor); textSize = 13f
            setPadding(px(8f), px(20f), px(8f), 0)
        })

        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }
    }

    private fun sectionTitle(s: String) = TextView(this).apply {
        text = s
        setTextColor(subColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(px(8f), px(24f), 0, px(8f))
    }

    private fun card(vararg rows: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 22 * dp
            setColor(ContextCompat.getColor(this@AboutActivity, R.color.mk_card))
        }
        setPadding(px(4f), px(6f), px(4f), px(6f))
        rows.forEach { addView(it) }
    }

    private fun row(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(16f), px(12f), px(16f), px(12f))
        addView(TextView(context).apply { text = title; setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f) })
        addView(TextView(context).apply { text = value; setTextColor(subColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) })
    }

    private fun note(s: String) = TextView(this).apply {
        text = s
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.15f)
        setPadding(px(16f), px(12f), px(16f), px(12f))
    }

    private fun link(title: String, value: String, url: String) = row(title, value).apply {
        isClickable = true
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        setBackgroundResource(out.resourceId)
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    }
}
