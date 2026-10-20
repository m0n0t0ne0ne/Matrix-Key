package com.matrixkey.keyboard

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Icons drawn straight onto the canvas from simple 24×24 paths.
 * With the dot font they're stroked as rows of round dots, matching the
 * dot-matrix look; otherwise as clean solid lines.
 */
enum class Icon { SHIFT, CAPS, BACKSPACE, ENTER, SEARCH, SEND, NEXT, DONE, EMOJI, MIC, CHEVRON_RIGHT, CHEVRON_LEFT, CLIPBOARD, GIF, AI, SETTINGS, CLOSE, PIN, KEYBOARD, GRID }

object Icons {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val matrix = Matrix()
    private val tmp = Path()

    private fun p(build: Path.() -> Unit) = Path().apply(build)

    private fun Path.circle(cx: Float, cy: Float, r: Float) = addCircle(cx, cy, r, Path.Direction.CW)
    private fun Path.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) =
        addRoundRect(RectF(l, t, r, b), rad, rad, Path.Direction.CW)

    private val strokes: Map<Icon, Path> = mapOf(
        Icon.SHIFT to p { moveTo(5.5f, 15.5f); lineTo(12f, 9f); lineTo(18.5f, 15.5f) },
        Icon.CAPS to p { moveTo(5.5f, 13.5f); lineTo(12f, 7f); lineTo(18.5f, 13.5f); moveTo(7f, 18f); lineTo(17f, 18f) },
        Icon.BACKSPACE to p {
            moveTo(9f, 6f); lineTo(20.5f, 6f); lineTo(20.5f, 18f); lineTo(9f, 18f); lineTo(3.5f, 12f); close()
            moveTo(12f, 9.5f); lineTo(17f, 14.5f); moveTo(17f, 9.5f); lineTo(12f, 14.5f)
        },
        Icon.ENTER to p { moveTo(19f, 5.5f); lineTo(19f, 12f); lineTo(5.5f, 12f); moveTo(9.5f, 8f); lineTo(5.5f, 12f); lineTo(9.5f, 16f) },
        Icon.SEARCH to p { circle(10.5f, 10.5f, 6.5f); moveTo(15.5f, 15.5f); lineTo(20.5f, 20.5f) },
        Icon.SEND to p { moveTo(4f, 12f); lineTo(20f, 12f); moveTo(14f, 6f); lineTo(20f, 12f); lineTo(14f, 18f) },
        Icon.NEXT to p { moveTo(5f, 12f); lineTo(18f, 12f); moveTo(13f, 7f); lineTo(18f, 12f); lineTo(13f, 17f) },
        Icon.DONE to p { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7.5f) },
        Icon.EMOJI to p { circle(12f, 12f, 8.5f); moveTo(8.5f, 14f); quadTo(12f, 17.5f, 15.5f, 14f) },
        Icon.MIC to p { moveTo(6f, 11f); arcTo(RectF(6f, 5f, 18f, 17f), 180f, -180f, false); moveTo(12f, 17f); lineTo(12f, 21f); moveTo(9f, 21f); lineTo(15f, 21f) },
        Icon.CHEVRON_RIGHT to p { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) },
        Icon.CHEVRON_LEFT to p { moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f) },
        Icon.CLIPBOARD to p { roundRect(5f, 5f, 19f, 21f, 2f); roundRect(9f, 3f, 15f, 7f, 1f); moveTo(8.5f, 11f); lineTo(15.5f, 11f); moveTo(8.5f, 14.5f); lineTo(15.5f, 14.5f); moveTo(8.5f, 18f); lineTo(13f, 18f) },
        Icon.GIF to p { moveTo(12f, 3f); lineTo(21f, 12f); lineTo(12f, 21f); lineTo(3f, 12f); close(); moveTo(12f, 8.5f); lineTo(12f, 15.5f); moveTo(8.5f, 12f); lineTo(15.5f, 12f) },
        Icon.AI to p { moveTo(14.5f, 6.5f); lineTo(17.5f, 9.5f); lineTo(9f, 18f); lineTo(5.5f, 18.5f); lineTo(6f, 15f); close(); moveTo(5f, 3f); lineTo(5f, 7.5f); moveTo(2.75f, 5.25f); lineTo(7.25f, 5.25f); moveTo(19f, 2.5f); lineTo(19f, 5.5f); moveTo(17.5f, 4f); lineTo(20.5f, 4f) },
        Icon.SETTINGS to p { circle(12f, 12f, 3f); circle(12f, 12f, 8f) },
        Icon.CLOSE to p { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) },
        Icon.PIN to p { moveTo(9f, 4f); lineTo(15f, 4f); moveTo(10f, 4f); lineTo(10f, 10f); lineTo(7f, 14f); lineTo(17f, 14f); lineTo(14f, 10f); lineTo(14f, 4f); moveTo(12f, 14f); lineTo(12f, 20f) },
        Icon.KEYBOARD to p { roundRect(3f, 6f, 21f, 18f, 2f); moveTo(7f, 10f); lineTo(7.1f, 10f); moveTo(11f, 10f); lineTo(11.1f, 10f); moveTo(15f, 10f); lineTo(15.1f, 10f); moveTo(8f, 14.5f); lineTo(16f, 14.5f) },
        Icon.GRID to p { roundRect(4f, 4f, 11f, 11f, 2f); roundRect(13f, 4f, 20f, 11f, 2f); roundRect(4f, 13f, 11f, 20f, 2f); roundRect(13f, 13f, 20f, 20f, 2f) },
    )

    /** Parts that are always filled (eyes, mic capsule, gear teeth). */
    private val fills: Map<Icon, Path> = mapOf(
        Icon.EMOJI to p { circle(9f, 10f, 1.4f); circle(15f, 10f, 1.4f) },
        Icon.MIC to p { roundRect(9f, 3f, 15f, 14f, 3f) },
    )

    fun draw(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int, dotted: Boolean, filledShift: Boolean = false) {
        val scale = size / 24f
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(cx - 12f * scale, cy - 12f * scale)

        paint.color = color
        strokes[icon]?.let { src ->
            tmp.reset()
            src.transform(matrix, tmp)
            paint.style = Paint.Style.STROKE
            if (dotted && icon != Icon.SETTINGS) {
                paint.strokeWidth = 2.5f * scale
                paint.pathEffect = DashPathEffect(floatArrayOf(0.01f, 3.4f * scale), 0f)
            } else {
                paint.strokeWidth = 2f * scale
                paint.pathEffect = if (icon == Icon.SETTINGS) null else null
            }
            if (icon == Icon.SETTINGS) {
                // Gear: dashed thick ring reads as teeth.
                paint.strokeWidth = 3.2f * scale
                paint.pathEffect = DashPathEffect(floatArrayOf(2.6f * scale, 1.9f * scale), 0f)
            }
            canvas.drawPath(tmp, paint)
            paint.pathEffect = null
        }
        fills[icon]?.let { src ->
            tmp.reset()
            src.transform(matrix, tmp)
            paint.style = Paint.Style.FILL
            canvas.drawPath(tmp, paint)
        }
        if (icon == Icon.SHIFT && filledShift) {
            tmp.reset()
            p { moveTo(5.5f, 15.5f); lineTo(12f, 9f); lineTo(18.5f, 15.5f); close() }.transform(matrix, tmp)
            paint.style = Paint.Style.FILL
            canvas.drawPath(tmp, paint)
        }
    }
}
