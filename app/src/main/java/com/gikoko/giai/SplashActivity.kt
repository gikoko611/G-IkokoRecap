package com.gikoko.giai

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

class SplashActivity : Activity() {
    private var t = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = object : View(this) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = Neon.dp(this@SplashActivity, 2).toFloat()
                color = Neon.GREEN
            }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Neon.GREEN
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Neon.DIM
                textAlign = Paint.Align.CENTER
            }

            override fun onDraw(c: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                c.drawColor(Neon.BG)
                glow.shader = RadialGradient(w / 2, h * 0.38f, w * 0.5f,
                    intArrayOf(Color.parseColor("#0D5C3A"), Neon.BG), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, glow)
                val cx = w / 2; val cy = h * 0.38f; val base = w * 0.28f
                for (i in 0 until 3) {
                    val phase = t * 2f * Math.PI.toFloat() + i * 2.1f
                    val r = base * (0.7f + 0.15f * i) * (0.92f + 0.08f * sin(phase))
                    c.save()
                    c.translate(cx, cy)
                    c.rotate(t * 120f * (if (i % 2 == 0) 1 else -1) + i * 30f)
                    c.scale(1f, 0.42f + 0.06f * i, 0f, 0f)
                    ring.alpha = (140 - i * 35)
                    c.drawCircle(0f, 0f, r, ring)
                    val px = r * cos(phase * 0.7f)
                    val py = r * sin(phase * 0.7f) * (0.42f + 0.06f * i)
                    c.drawCircle(px, py, Neon.dp(context, 4).toFloat(), ring)
                    c.restore()
                }
                text.textSize = Neon.dp(context, 52).toFloat()
                text.alpha = ((0.5f + 0.5f * t) * 255f).toInt().coerceIn(0, 255)
                c.drawText("G.I", cx, cy + Neon.dp(context, 14).toFloat(), text)
                sub.textSize = Neon.dp(context, 16).toFloat()
                sub.color = Neon.GREEN
                c.drawText("A I   N E U R A L   C O R E", cx, cy + Neon.dp(context, 44).toFloat(), sub)
                sub.color = Neon.DIM
                sub.textSize = Neon.dp(context, 12).toFloat()
                c.drawText("Powered by I KO KO", cx, h - Neon.dp(context, 40).toFloat(), sub)
            }
        }
        setContentView(view)
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 2200
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener { a ->
            t = a.animatedValue as Float
            view.invalidate()
        }
        anim.start()
        view.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2400)
    }
}
