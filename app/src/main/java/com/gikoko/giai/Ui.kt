package com.gikoko.giai

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat

object Neon {
    val GREEN = Color.parseColor("#00FF9C")
    val BG = Color.parseColor("#03140C")
    val SURFACE = Color.parseColor("#062015")
    val CARD = Color.parseColor("#0A2B1D")
    val TEXT = Color.parseColor("#D8FFE9")
    val DIM = Color.parseColor("#5FA986")
    val DIM2 = Color.parseColor("#0A3D2A")

    fun dp(c: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt()

    fun label(c: Context, text: String, size: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun btn(c: Context, text: String, onClick: (View) -> Unit): Button {
        val b = Button(c)
        b.text = text
        b.allCaps = false
        b.textSize = 13f
        b.setTextColor(GREEN)
        b.background = ContextCompat.getDrawable(c, R.drawable.bg_card)
        b.setOnClickListener(onClick)
        b.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 4))
        return b
    }

    fun section(c: Context, title: String): TextView {
        val t = label(c, title, 15f, GREEN, true)
        t.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 6))
        return t
    }

    fun input(c: Context, hint: String, value: String = "", password: Boolean = false): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setText(value)
        e.setTextColor(TEXT)
        e.setHintTextColor(DIM)
        e.textSize = 13f
        e.background = ContextCompat.getDrawable(c, R.drawable.bg_input)
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10))
        if (password) e.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        return e
    }

    fun note(c: Context, text: String): TextView {
        val t = label(c, text, 12f, DIM)
        t.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 4))
        return t
    }
}
