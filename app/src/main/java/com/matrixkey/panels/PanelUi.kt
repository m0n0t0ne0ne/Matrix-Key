package com.matrixkey.panels

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.matrixkey.theme.KeyboardTheme

/** Small helpers so every panel is styled from the same keyboard theme. */
class PanelUi(val context: Context, val theme: KeyboardTheme, val dotTypeface: Typeface) {
    val dp = context.resources.displayMetrics.density
    fun px(v: Float) = (v * dp).toInt()

    fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radiusDp * dp }

    fun pressable(view: View, color: Int, radiusDp: Float) {
        val ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.text, 40))
        view.background = RippleDrawable(ripple, rounded(color, radiusDp), rounded(0xFFFFFFFF.toInt(), radiusDp))
        view.isClickable = true
        view.isFocusable = true
    }

    fun text(s: String, sizeSp: Float = 15f, color: Int = theme.text, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = s
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    fun title(s: String): TextView = TextView(context).apply {
        text = s
        setTextColor(theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = dotTypeface
    }

    /** A pill button. [filled] uses the accent color. */
    fun chip(s: String, filled: Boolean = false, onClick: () -> Unit): TextView = TextView(context).apply {
        text = s
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(if (filled) theme.onAccent else theme.text)
        setPadding(px(16f), 0, px(16f), 0)
        minHeight = px(40f)
        minimumHeight = px(40f)
        pressable(this, if (filled) theme.accent else theme.key, 20f)
        setOnClickListener { onClick() }
    }

    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun column(): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun lp(w: Int, h: Int, weight: Float = 0f, marginDp: Float = 0f) = LinearLayout.LayoutParams(w, h, weight).apply {
        val m = px(marginDp); setMargins(m, m, m, m)
    }

    fun gap(widthDp: Float = 8f) = View(context).apply { layoutParams = LinearLayout.LayoutParams(px(widthDp), px(widthDp)) }
}
