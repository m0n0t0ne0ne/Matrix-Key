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
enum class Icon { SCAN, SHIFT, CAPS, BACKSPACE, ENTER, SEARCH, SEND, NEXT, DONE, EMOJI, MIC, CHEVRON_RIGHT, CHEVRON_LEFT, CLIPBOARD, GIF, AI, SETTINGS, CLOSE, PIN, KEYBOARD, GRID }

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
        Icon.SHIFT to p { moveTo(12f, 3.5f); lineTo(3.5f, 12f); lineTo(8.5f, 12f); lineTo(8.5f, 19.5f); lineTo(15.5f, 19.5f); lineTo(15.5f, 12f); lineTo(20.5f, 12f); close() },
        Icon.CAPS to p { moveTo(12f, 2.5f); lineTo(3.5f, 11f); lineTo(8.5f, 11f); lineTo(8.5f, 16.5f); lineTo(15.5f, 16.5f); lineTo(15.5f, 11f); lineTo(20.5f, 11f); close(); moveTo(8.5f, 20.5f); lineTo(15.5f, 20.5f) },
        Icon.BACKSPACE to p {
            moveTo(9f, 6f); lineTo(20.5f, 6f); lineTo(20.5f, 18f); lineTo(9f, 18f); lineTo(3.5f, 12f); close()
            moveTo(12f, 9.5f); lineTo(17f, 14.5f); moveTo(17f, 9.5f); lineTo(12f, 14.5f)
        },
        Icon.ENTER to p { moveTo(19.5f, 6f); lineTo(19.5f, 12.5f); lineTo(5f, 12.5f); moveTo(9.5f, 8f); lineTo(5f, 12.5f); lineTo(9.5f, 17f) },
        Icon.SEARCH to p { circle(10.5f, 10.5f, 6.5f); moveTo(15.5f, 15.5f); lineTo(20.5f, 20.5f) },
        Icon.SEND to p { moveTo(4f, 12f); lineTo(20f, 12f); moveTo(14f, 6f); lineTo(20f, 12f); lineTo(14f, 18f) },
        Icon.NEXT to p { moveTo(5f, 12f); lineTo(18f, 12f); moveTo(13f, 7f); lineTo(18f, 12f); lineTo(13f, 17f) },
        Icon.DONE to p { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7.5f) },
        Icon.EMOJI to p { circle(12f, 12f, 8.5f); moveTo(8.5f, 14f); quadTo(12f, 17.5f, 15.5f, 14f) },
        Icon.MIC to p { moveTo(6f, 11f); arcTo(RectF(6f, 5f, 18f, 17f), 180f, -180f, false); moveTo(12f, 17f); lineTo(12f, 21f); moveTo(9f, 21f); lineTo(15f, 21f) },
        Icon.CHEVRON_RIGHT to p { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) },
        Icon.CHEVRON_LEFT to p { moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f) },
        Icon.CLIPBOARD to p { roundRect(5f, 5f, 19f, 21f, 2f); roundRect(9f, 3f, 15f, 7f, 1f); moveTo(8.5f, 11f); lineTo(15.5f, 11f); moveTo(8.5f, 14.5f); lineTo(15.5f, 14.5f); moveTo(8.5f, 18f); lineTo(13f, 18f) },
        Icon.AI to p { moveTo(14.5f, 6.5f); lineTo(17.5f, 9.5f); lineTo(9f, 18f); lineTo(5.5f, 18.5f); lineTo(6f, 15f); close(); moveTo(5f, 3f); lineTo(5f, 7.5f); moveTo(2.75f, 5.25f); lineTo(7.25f, 5.25f); moveTo(19f, 2.5f); lineTo(19f, 5.5f); moveTo(17.5f, 4f); lineTo(20.5f, 4f) },
        Icon.SETTINGS to p { circle(12f, 12f, 3f); circle(12f, 12f, 8f) },
        Icon.CLOSE to p { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) },
        Icon.PIN to p { moveTo(9f, 4f); lineTo(15f, 4f); moveTo(10f, 4f); lineTo(10f, 10f); lineTo(7f, 14f); lineTo(17f, 14f); lineTo(14f, 10f); lineTo(14f, 4f); moveTo(12f, 14f); lineTo(12f, 20f) },
        Icon.KEYBOARD to p { roundRect(3f, 6f, 21f, 18f, 2f); moveTo(7f, 10f); lineTo(7.1f, 10f); moveTo(11f, 10f); lineTo(11.1f, 10f); moveTo(15f, 10f); lineTo(15.1f, 10f); moveTo(8f, 14.5f); lineTo(16f, 14.5f) },
        Icon.SCAN to p { moveTo(4f, 8f); lineTo(4f, 5f); quadTo(4f, 4f, 5f, 4f); lineTo(8f, 4f); moveTo(16f, 4f); lineTo(19f, 4f); quadTo(20f, 4f, 20f, 5f); lineTo(20f, 8f); moveTo(20f, 16f); lineTo(20f, 19f); quadTo(20f, 20f, 19f, 20f); lineTo(16f, 20f); moveTo(8f, 20f); lineTo(5f, 20f); quadTo(4f, 20f, 4f, 19f); lineTo(4f, 16f); moveTo(8f, 9f); lineTo(16f, 9f); moveTo(8f, 12f); lineTo(16f, 12f); moveTo(8f, 15f); lineTo(13f, 15f) },
        Icon.GRID to p { roundRect(4f, 4f, 11f, 11f, 2f); roundRect(13f, 4f, 20f, 11f, 2f); roundRect(4f, 13f, 11f, 20f, 2f); roundRect(13f, 13f, 20f, 20f, 2f) },
    )

    /** Parts that are always filled (eyes, mic capsule). */
    private val fills: Map<Icon, Path> = mapOf(
        Icon.EMOJI to p { circle(9f, 10f, 1.4f); circle(15f, 10f, 1.4f) },
        Icon.MIC to p { roundRect(9f, 3f, 15f, 14f, 3f) },
    )

    /** Solid (Gboard-style) icons drawn as one filled shape instead of strokes. */
    private val solidFills: Map<Icon, Path> = mapOf(
        Icon.GRID to p { roundRect(3.5f, 3.5f, 10.5f, 10.5f, 2.2f); roundRect(13.5f, 3.5f, 20.5f, 10.5f, 2.2f); roundRect(3.5f, 13.5f, 10.5f, 20.5f, 2.2f); roundRect(13.5f, 13.5f, 20.5f, 20.5f, 2.2f) },
        Icon.SETTINGS to gear(),
    )

    private val gifText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.04f
    }

    /** "GIF" in bold letters inside a rounded badge, like the label on GIF buttons everywhere. */
    private fun drawGif(canvas: Canvas, color: Int, scale: Float, dotted: Boolean) {
        val r = RectF(1.5f, 5f, 22.5f, 19f)
        matrix.mapRect(r)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.pathEffect = if (dotted) DashPathEffect(floatArrayOf(0.01f, 3.0f * scale), 0f) else null
        canvas.drawRoundRect(r, 4f * scale, 4f * scale, paint)
        paint.pathEffect = null
        gifText.color = color
        gifText.textSize = 8.6f * scale
        val fm = gifText.fontMetrics
        canvas.drawText("GIF", r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, gifText)
    }

    /** A filled gear with a round hole, like Material's settings icon. */
    private fun gear(): Path {
        val path = Path()
        val teeth = 8
        val outer = 10.2f; val inner = 7.6f
        val steps = teeth * 4
        for (i in 0..steps) {
            val a = (Math.PI * 2 * i / steps - Math.PI / 2 + Math.PI / steps).toFloat()
            val r = if ((i / 2) % 2 == 0) outer else inner
            val x = 12f + r * kotlin.math.cos(a); val y = 12f + r * kotlin.math.sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        path.addCircle(12f, 12f, 3.3f, Path.Direction.CW)
        path.fillType = Path.FillType.EVEN_ODD
        return path
    }

    fun draw(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int, dotted: Boolean, filledShift: Boolean = false) {
        val scale = size / 24f
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(cx - 12f * scale, cy - 12f * scale)

        paint.color = color
        if (icon == Icon.GIF) { drawGif(canvas, color, scale, dotted); return }
        val solid = if (!dotted) solidFills[icon] else null
        if (solid != null) {
            tmp.reset()
            solid.transform(matrix, tmp)
            tmp.fillType = solid.fillType
            paint.style = Paint.Style.FILL
            canvas.drawPath(tmp, paint)
            return
        }
        strokes[icon]?.let { src ->
            tmp.reset()
            src.transform(matrix, tmp)
            paint.style = Paint.Style.STROKE
            if (dotted && icon != Icon.SETTINGS) {
                paint.strokeWidth = 2.5f * scale
                paint.pathEffect = DashPathEffect(floatArrayOf(0.01f, 3.4f * scale), 0f)
            } else {
                paint.strokeWidth = 2f * scale
                paint.pathEffect = null
            }
            if (icon == Icon.SETTINGS) {
                // Dotted gear: a dashed thick ring reads as teeth.
                paint.strokeWidth = 3.2f * scale
                paint.pathEffect = DashPathEffect(floatArrayOf(2.6f * scale, 1.9f * scale), 0f)
            }
            if ((icon == Icon.SHIFT && filledShift) || icon == Icon.CAPS) {
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.pathEffect = null
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
    }
}
