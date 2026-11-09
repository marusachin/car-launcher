package com.carlauncher.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 3D style speedometer drawn with gradients. No images, light on memory. */
class SpeedometerView(context: Context) : View(context) {

    private val maxSpeed = 240f
    private val accent = 0xFF4FB3FF.toInt()
    private val muted = 0xFF9AA6B2.toInt()
    private var shown = 0f
    private var animator: ValueAnimator? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val needle = Path()
    private val oval = RectF()
    private val box = RectF()

    fun setSpeed(kmh: Float) {
        val target = kmh.coerceIn(0f, maxSpeed)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 600
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.97f
        if (r <= 0f) return

        // chrome bezel
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            intArrayOf(0xFF8B95A1.toInt(), 0xFF2A3038.toInt(), 0xFF14171B.toInt(), 0xFF6B7580.toInt()),
            floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, p)

        // inner bevel
        p.shader = LinearGradient(
            cx + r, cy + r, cx - r, cy - r,
            intArrayOf(0xFF4A525C.toInt(), 0xFF0D0F12.toInt(), 0xFF2A3038.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 0.935f, p)

        p.shader = null
        p.color = 0xFF0D0F12.toInt()
        canvas.drawCircle(cx, cy, r * 0.889f, p)

        // glowing speed arc
        val arcR = r * 0.889f
        oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.037f
        p.color = 0xFF2A3038.toInt()
        canvas.drawArc(oval, 135f, 270f, false, p)
        val sweep = 270f * shown / maxSpeed
        if (sweep > 0.5f) {
            p.color = accent
            p.alpha = 40; p.strokeWidth = r * 0.11f
            canvas.drawArc(oval, 135f, sweep, false, p)
            p.alpha = 90; p.strokeWidth = r * 0.07f
            canvas.drawArc(oval, 135f, sweep, false, p)
            p.alpha = 255; p.strokeWidth = r * 0.037f
            canvas.drawArc(oval, 135f, sweep, false, p)
        }

        // dial face
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx - r * 0.2f, cy - r * 0.3f, r * 0.9f,
            intArrayOf(0xFF2E3640.toInt(), 0xFF15191E.toInt(), 0xFF0A0C0F.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 0.852f, p)

        // ticks
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF8B95A1.toInt()
        for (i in 0..27) {
            val a = Math.toRadians((135 + i * 10).toDouble())
            val big = i % 3 == 0
            val r1 = r * (if (big) 0.71f else 0.75f)
            val r2 = r * 0.82f
            p.strokeWidth = r * (if (big) 0.022f else 0.012f)
            canvas.drawLine(
                cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), p
            )
        }

        // scale numbers
        tp.color = muted
        tp.textSize = r * 0.1f
        for (k in 0..4) {
            val a = Math.toRadians(135.0 + 67.5 * k)
            val x = cx + (r * 0.58f * cos(a)).toFloat()
            val y = cy + (r * 0.58f * sin(a)).toFloat() + tp.textSize / 3f
            canvas.drawText((k * 60).toString(), x, y, tp)
        }

        // digital readout window
        val by = cy + r * 0.45f
        box.set(cx - r * 0.27f, by - r * 0.175f, cx + r * 0.27f, by + r * 0.175f)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.color = 0xFF0D0F12.toInt()
        canvas.drawRoundRect(box, r * 0.07f, r * 0.07f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.01f
        p.color = 0xFF2A3038.toInt()
        canvas.drawRoundRect(box, r * 0.07f, r * 0.07f, p)
        tp.color = 0xFFE6EEF5.toInt()
        tp.textSize = r * 0.19f
        canvas.drawText(shown.toInt().toString(), cx, by + r * 0.01f, tp)
        tp.color = muted
        tp.textSize = r * 0.085f
        canvas.drawText("km/h", cx, by + r * 0.13f, tp)

        // needle with shadow
        val ang = 135f + 270f * shown / maxSpeed
        needle.reset()
        needle.moveTo(cx + r * 0.68f, cy)
        needle.lineTo(cx, cy - r * 0.035f)
        needle.lineTo(cx - r * 0.12f, cy)
        needle.lineTo(cx, cy + r * 0.035f)
        needle.close()
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        canvas.save()
        canvas.translate(r * 0.025f, r * 0.04f)
        canvas.rotate(ang, cx, cy)
        p.color = 0xFF000000.toInt(); p.alpha = 120
        canvas.drawPath(needle, p)
        canvas.restore()
        canvas.save()
        canvas.rotate(ang, cx, cy)
        p.color = accent
        canvas.drawPath(needle, p)
        canvas.restore()

        // metal hub
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx - r * 0.03f, cy - r * 0.03f, r * 0.1f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF9AA6B2.toInt(), 0xFF3A424B.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 0.09f, p)
        p.shader = null
        p.color = 0xFF15181C.toInt()
        canvas.drawCircle(cx, cy, r * 0.037f, p)

        // glass shine
        p.color = 0xFFFFFFFF.toInt(); p.alpha = 13
        canvas.drawOval(RectF(cx - r * 0.61f, cy - r * 0.73f, cx + r * 0.61f, cy - r * 0.11f), p)
    }
}
