package com.matrixkey

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * The keyboard's root view. Its height comes only from [content] (the
 * toolbar + keys). The background image and the popup/trail overlay are then
 * stretched to exactly that size, so nothing can push the keyboard to fill
 * the whole screen.
 */
@SuppressLint("ViewConstructor")
class KeyboardRoot(context: Context, private val content: View) : FrameLayout(context) {

    /** Called for every touch on the keyboard (keys, bar, panels). */
    var onTouch: (() -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        onTouch?.invoke()
        return super.dispatchTouchEvent(ev)
    }

    init {
        setPadding(0, 0, 0, 0)
        fitsSystemWindows = false
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // Ignore any padding the system tries to apply for insets.
        super.setPadding(0, 0, 0, 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val exactW = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        content.measure(exactW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val height = content.measuredHeight
        val exactH = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== content && child.visibility != GONE) child.measure(exactW, exactH)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility != GONE) c.layout(0, 0, w, c.measuredHeight)
        }
    }
}
