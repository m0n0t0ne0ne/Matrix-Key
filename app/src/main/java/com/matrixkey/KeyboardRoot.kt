package com.matrixkey

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * The keyboard's root view. Its height comes only from [content] (the
 * toolbar + keys). The background image and the popup/trail overlay are then
 * stretched to exactly that size, so they can never push the keyboard to
 * fill the whole screen.
 */
@SuppressLint("ViewConstructor")
class KeyboardRoot(context: Context, private val content: View) : FrameLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val innerW = MeasureSpec.makeMeasureSpec(width - paddingLeft - paddingRight, MeasureSpec.EXACTLY)
        content.measure(innerW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val height = content.measuredHeight + paddingTop + paddingBottom
        val exactH = MeasureSpec.makeMeasureSpec(content.measuredHeight, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== content && child.visibility != GONE) child.measure(innerW, exactH)
        }
        setMeasuredDimension(width, height)
    }
}
