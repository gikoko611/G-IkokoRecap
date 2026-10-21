package com.gikoko.recap.recap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

object FrameRenderer {

    fun render(title: String, text: String, index: Int, outFile: File) {
        val w = 1280
        val h = 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#05070E"))

        val grid = Paint()
        grid.isAntiAlias = true
        grid.color = Color.parseColor("#13224A")
        grid.strokeWidth = 2f
        var x = 0
        while (x < w) {
            c.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), grid)
            x += 80
        }
        var y = 0
        while (y < h) {
            c.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), grid)
            y += 80
        }

        val border = Paint()
        border.isAntiAlias = true
        border.color = Color.parseColor("#FFC940")
        border.strokeWidth = 6f
        border.style = Paint.Style.STROKE
        c.drawRect(20f, 20f, (w - 20).toFloat(), (h - 20).toFloat(), border)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = Color.parseColor("#3DE8F8")
        tp.textSize = 44f
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        if (title.length > 40) {
            c.drawText(title.substring(0, 40), 60f, 100f, tp)
        } else {
            c.drawText(title, 60f, 100f, tp)
        }

        tp.color = Color.parseColor("#FFC940")
        tp.textSize = 30f
        c.drawText("SEGMENT " + (index + 1), 60f, 160f, tp)

        val bp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        bp.color = Color.WHITE
        bp.textSize = 42f
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, bp, w - 140)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(14f, 1.0f)
            .build()
        c.save()
        c.translate(70f, 240f)
        layout.draw(c)
        c.restore()

        tp.color = Color.parseColor("#8A93B2")
        tp.textSize = 26f
        c.drawText("GIkoko One Recap", 60f, (h - 60).toFloat(), tp)

        val fo = FileOutputStream(outFile)
        fo.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }
}
