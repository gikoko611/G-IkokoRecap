package com.gikoko.recap

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.logo)
        logo.alpha = 0f
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f

        val fadeIn = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f)
        fadeIn.startDelay = 400
        fadeIn.duration = 900

        val sx = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.7f, 1f)
        val sy = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.7f, 1f)
        sx.duration = 1200
        sy.duration = 1200

        val set = AnimatorSet()
        set.playTogether(fadeIn, sx, sy)
        set.interpolator = DecelerateInterpolator()
        set.start()

        logo.postDelayed({
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }, 2600)
    }
}
