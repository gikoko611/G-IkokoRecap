package com.gikoko.recap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class CyberSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint()
    private val linePaint = Paint()
    private val dotPaint = Paint()
    private val glowPaint = Paint()
    private val sweepPaint = Paint()
    private var t = 0L
    private val particles = ArrayList<FloatArray>()

    init {
        bgPaint.color = Color.parseColor("#05070E")
        linePaint.isAntiAlias = true
        linePaint.color = Color.parseColor("#13224A")
        linePaint.strokeWidth = 1.2f
        dotPaint.isAntiAlias = true
        glowPaint.isAntiAlias = true
        sweepPaint.isAntiAlias = true
        sweepPaint.style = Paint.Style.STROKE
        sweepPaint.strokeWidth = 2.5f

        val rnd = Random(11)
        for (i in 0 until 70) {
            particles.add(floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.25f + rnd.nextFloat(), 0.4f + rnd.nextFloat() * 1.8f))
        }

        val anim = ValueAnimator.ofInt(0, 1)
        anim.duration = 16
        anim.repeatCount = ValueAnimator.INFINITE
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener {
            t += 16
            invalidate()
        }
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val horizon = h * 0.42f
        val cx = w / 2f

        glowPaint.shader = RadialGradient(
            cx, horizon, w * 0.6f,
            Color.parseColor("#263DE8F8"),
            Color.parseColor("#0005070E"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        glowPaint.shader = null

        val n = 14
        for (i in -n..n) {
            val topX = cx + i * (w / (n * 0.8f)) * 0.05f
            val bottomX = cx + i * (w / (n * 0.8f))
            canvas.drawLine(topX, horizon, bottomX, h, linePaint)
        }

        val period = 2400f
        for (i in 0 until 9) {
            val phase = (((t % period.toLong()).toFloat() + i * (period / 9f)) / period) % 1f
            val yy = horizon + (h - horizon) * phase * phase
            canvas.drawLine(0f, yy, w, yy, linePaint)
        }

        for (p in particles) {
            val px = p[0] * w
            val py = ((p[1] + t * 0.00003f * p[2]) % 1f) * horizon
            val a = 0.25f + 0.5f * abs(sin(t * 0.002f * p[3] + p[0] * 6.28f))
            dotPaint.color = if (p[3] > 1.4f) Color.parseColor("#FFC940") else Color.parseColor("#3DE8F8")
            dotPaint.alpha = (a * 255).toInt()
            canvas.drawCircle(px, py, 1f + p[2] * 1.6f, dotPaint)
        }

        sweepPaint.color = Color.parseColor("#FFC940")
        sweepPaint.alpha = 70
        val startA = (t % 5000) / 5000f * 360f
        canvas.drawArc(cx - w * 0.42f, horizon - w * 0.42f, cx + w * 0.42f, horizon + w * 0.42f, startA, 80f, false, sweepPaint)
        sweepPaint.color = Color.parseColor("#3DE8F8")
        sweepPaint.alpha = 50
        canvas.drawArc(cx - w * 0.36f, horizon - w * 0.36f, cx + w * 0.36f, horizon + w * 0.36f, startA + 180f, 60f, false, sweepPaint)
    }
}
