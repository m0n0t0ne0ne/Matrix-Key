package com.matrixkey.settings

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.matrixkey.R
import com.matrixkey.theme.FontManager

private fun Context.dpf(v: Float) = v * resources.displayMetrics.density
private fun Context.dpi(v: Float) = (v * resources.displayMetrics.density).toInt()

/**
 * Section header. With a title it's a small dot-font label; without one it's
 * just a gap between groups of tiles (like Nothing's settings home).
 */
class DotCategory @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PreferenceCategory(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val ctx = holder.itemView.context
        val titleView = holder.findViewById(android.R.id.title) as? TextView
        if (title.isNullOrBlank()) {
            titleView?.visibility = View.GONE
            holder.itemView.minimumHeight = 0
            holder.itemView.setPadding(0, ctx.dpi(14f), 0, 0)
            return
        }
        titleView?.apply {
            visibility = View.VISIBLE
            typeface = FontManager.ndot(ctx)
            setTextColor(ContextCompat.getColor(ctx, R.color.mk_sub))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            letterSpacing = 0.08f
            text = title?.toString()?.uppercase()
            setPadding(ctx.dpi(8f), 0, 0, 0)
        }
        holder.itemView.setPadding(holder.itemView.paddingLeft, ctx.dpi(20f), holder.itemView.paddingRight, ctx.dpi(8f))
    }
}

/** Dot-font text span. */
fun dotTitle(context: Context, s: String): CharSequence = SpannableString(s).apply {
    setSpan(TypefaceSpan(FontManager.ndot(context)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/** Big dot-matrix page title with an optional round back button, as on Nothing OS. */
fun pageHeader(activity: Activity, title: String, showBack: Boolean, onBack: () -> Unit): Pair<View, TextView> {
    val ctx = activity
    val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dpi(16f), ctx.dpi(12f), ctx.dpi(16f), 0)
    }
    val topRow = FrameLayout(ctx)
    val back = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_mk_back)
        scaleType = ImageView.ScaleType.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(ctx, R.color.mk_card))
        }
        contentDescription = "Back"
        setOnClickListener { onBack() }
        visibility = if (showBack) View.VISIBLE else View.INVISIBLE
        tag = "back"
    }
    topRow.addView(back, FrameLayout.LayoutParams(ctx.dpi(52f), ctx.dpi(52f), Gravity.START or Gravity.CENTER_VERTICAL))
    col.addView(topRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dpi(56f)))
    val titleView = TextView(ctx).apply {
        text = title.uppercase()
        typeface = FontManager.ndot(ctx)
        setTextColor(ContextCompat.getColor(ctx, R.color.mk_text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 38f)
        letterSpacing = 0.04f
        maxLines = 2
        setPadding(ctx.dpi(8f), ctx.dpi(28f), 0, ctx.dpi(20f))
    }
    col.addView(titleView)
    return col to titleView
}

/** Rounded "Search settings" pill. */
fun searchPill(ctx: Context, onClick: () -> Unit): View {
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dpi(22f), 0, ctx.dpi(22f), 0)
        background = GradientDrawable().apply {
            cornerRadius = ctx.dpf(30f)
            setColor(ContextCompat.getColor(ctx, R.color.mk_card))
        }
        isClickable = true
        setOnClickListener { onClick() }
    }
    row.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_mk_search); alpha = 0.75f }, LinearLayout.LayoutParams(ctx.dpi(24f), ctx.dpi(24f)))
    row.addView(TextView(ctx).apply {
        text = "Search settings"
        setTextColor(ContextCompat.getColor(ctx, R.color.mk_sub))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(ctx.dpi(16f), 0, 0, 0)
    })
    return row
}

/**
 * Draws each row as its own tile, Nothing-style: tiles in a group sit 3dp
 * apart, with large rounded corners at the group's ends and small ones between.
 */
class CardDecoration(context: Context, private val headerAt: (Int) -> Boolean) : RecyclerView.ItemDecoration() {
    private val d = context.resources.displayMetrics.density
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.mk_card) }
    private val side = 16 * d
    private val big = 26 * d
    private val small = 6 * d
    private val gap = 3 * d
    private val path = Path()

    private fun isHeader(parent: RecyclerView, pos: Int): Boolean {
        val count = parent.adapter?.itemCount ?: 0
        if (pos < 0 || pos >= count) return true
        return headerAt(pos)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        outRect.left = side.toInt(); outRect.right = side.toInt()
        if (!isHeader(parent, pos) && !isHeader(parent, pos + 1)) outRect.bottom = gap.toInt()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(v)
            if (pos == RecyclerView.NO_POSITION || isHeader(parent, pos)) continue
            val first = isHeader(parent, pos - 1)
            val last = isHeader(parent, pos + 1)
            val r = RectF(v.left.toFloat(), v.top + v.translationY, v.right.toFloat(), v.bottom + v.translationY)
            val t = if (first) big else small
            val b = if (last) big else small
            path.reset()
            path.addRoundRect(r, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
            c.drawPath(path, card)
        }
    }
}
