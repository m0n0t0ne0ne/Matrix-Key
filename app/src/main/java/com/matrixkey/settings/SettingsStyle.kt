package com.matrixkey.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.matrixkey.R
import com.matrixkey.theme.FontManager

/** Section header drawn in the dot-matrix font, small caps-style and spaced out. */
class DotCategory @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PreferenceCategory(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(android.R.id.title) as? TextView)?.apply {
            typeface = FontManager.ndot(context)
            setTextColor(ContextCompat.getColor(context, R.color.mk_sub))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            letterSpacing = 0.08f
            text = title?.toString()?.uppercase()
            val d = resources.displayMetrics.density
            setPadding((8 * d).toInt(), (6 * d).toInt(), 0, 0)
        }
        holder.itemView.setPadding(holder.itemView.paddingLeft, (20 * context.resources.displayMetrics.density).toInt(), holder.itemView.paddingRight, (6 * context.resources.displayMetrics.density).toInt())
    }
}

/** Dot-font title for the action bar. */
fun dotTitle(context: Context, s: String): CharSequence = SpannableString(s).apply {
    setSpan(TypefaceSpan(FontManager.ndot(context)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/**
 * Groups the rows of each section into a rounded card (like Nothing's own
 * settings), with thin dividers between rows.
 */
class CardDecoration(context: Context, private val headerAt: (Int) -> Boolean) : RecyclerView.ItemDecoration() {
    private val d = context.resources.displayMetrics.density
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.mk_card) }
    private val line = Paint().apply { color = ContextCompat.getColor(context, R.color.mk_line) }
    private val side = 16 * d
    private val radius = 20 * d

    private fun isHeader(parent: RecyclerView, pos: Int): Boolean {
        val count = parent.adapter?.itemCount ?: 0
        if (pos < 0 || pos >= count) return true
        return headerAt(pos)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        outRect.left = side.toInt(); outRect.right = side.toInt()
        if (!isHeader(parent, pos) && isHeader(parent, pos + 1)) outRect.bottom = (4 * d).toInt()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(v)
            if (pos == RecyclerView.NO_POSITION || isHeader(parent, pos)) continue
            val first = isHeader(parent, pos - 1)
            val last = isHeader(parent, pos + 1)
            val r = RectF(v.left.toFloat(), v.top + v.translationY, v.right.toFloat(), v.bottom + v.translationY)
            // Round only the outer corners of each card.
            val path = android.graphics.Path()
            val radii = floatArrayOf(
                if (first) radius else 0f, if (first) radius else 0f,
                if (first) radius else 0f, if (first) radius else 0f,
                if (last) radius else 0f, if (last) radius else 0f,
                if (last) radius else 0f, if (last) radius else 0f,
            )
            path.addRoundRect(r, radii, android.graphics.Path.Direction.CW)
            c.drawPath(path, card)
            if (!last) c.drawRect(r.left + 20 * d, r.bottom - 0.5f * d, r.right - 20 * d, r.bottom + 0.5f * d, line)
        }
    }
}
