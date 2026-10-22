package com.gikoko.recap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

class NeonProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pct = 0f
    private var animPos = 0f
    private var animator: ValueAnimator? = null
    private val dp = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val shimmer = ValueAnimator.ofFloat(0f, 1f)

    init {
        trackPaint.color = Color.parseColor("#0E1730")
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = dp * 1.2f
        strokePaint.color = Color.parseColor("#2A3A66")
        tipPaint.color = Color.parseColor("#FFF3C9")
        textPaint.color = Color.parseColor("#FFE9A8")
        textPaint.textSize = dp * 12f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.setShadowLayer(dp * 3f, 0f, 0f, Color.parseColor("#66000000"))
        shimmer.duration = 1300L
        shimmer.repeatCount = ValueAnimator.INFINITE
        shimmer.interpolator = LinearInterpolator()
        shimmer.addUpdateListener { a ->
            animPos = a.animatedValue as Float
            invalidate()
        }
        shimmer.start()
    }

    fun setProgress(p: Int) {
        val target = p.coerceIn(0, 100).toFloat()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(pct, target)
        animator?.duration = 300L + (Math.abs(target - pct) * 220f).toLong()
        animator?.interpolator = DecelerateInterpolator()
        animator?.addUpdateListener { a ->
            pct = a.animatedValue as Float
            invalidate()
        }
        animator?.start()
    }

    fun setProgressSlow(p: Int, ms: Long) {
        val target = p.coerceIn(0, 100).toFloat()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(pct, target)
        animator?.duration = ms
        animator?.interpolator = LinearInterpolator()
        animator?.addUpdateListener { a ->
            pct = a.animatedValue as Float
            invalidate()
        }
        animator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = h / 2f

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val fw = w * pct / 100f
        if (fw > h) {
            fillPaint.shader = LinearGradient(
                0f, 0f, w, 0f,
                Color.parseColor("#FFC940"), Color.parseColor("#3DE8F8"),
                Shader.TileMode.CLAMP
            )
            rect.set(0f, 0f, fw, h)
            canvas.drawRoundRect(rect, r, r, fillPaint)

            val band = h * 1.8f
            val travel = fw + band * 2f
            val sx = animPos * travel - band
            shinePaint.shader = LinearGradient(
                sx, 0f, sx + band, 0f,
                Color.parseColor("#00FFFFFF"), Color.parseColor("#4DFFFFFF"),
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipRect(0f, 0f, fw, h)
            canvas.drawRect(sx, 0f, sx + band, h, shinePaint)
            canvas.restore()

            val glow = (170 + 60 * Math.sin((animPos * 6.28318f).toDouble())).toInt()
            tipPaint.alpha = glow.coerceIn(0, 255)
            canvas.drawCircle(fw - r, h / 2f, r * 0.6f, tipPaint)
        }

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, strokePaint)

        val textY = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(pct.toInt().toString() + "%", w / 2f, textY, textPaint)
    }
}
