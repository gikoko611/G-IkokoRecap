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

    fun render(title: String, text: String, index: Int, outFile: File, watermark: Boolean, vertical: Boolean = false) {
        val W = if (vertical) 720 else 1280
        val H = if (vertical) 1280 else 720
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#05070E"))

        val grid = Paint()
        grid.isAntiAlias = true
        grid.color = Color.parseColor("#13224A")
        grid.strokeWidth = 2f
        var x = 0
        while (x < W) {
            c.drawLine(x.toFloat(), 0f, x.toFloat(), H.toFloat(), grid)
            x += 80
        }
        var y = 0
        while (y < H) {
            c.drawLine(0f, y.toFloat(), W.toFloat(), y.toFloat(), grid)
            y += 80
        }

        val border = Paint()
        border.isAntiAlias = true
        border.color = Color.parseColor("#FFC940")
        border.strokeWidth = 6f
        border.style = Paint.Style.STROKE
        c.drawRect(20f, 20f, (W - 20).toFloat(), (H - 20).toFloat(), border)

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
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, bp, W - 140)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(14f, 1.0f)
            .build()
        c.save()
        c.translate(70f, 240f)
        layout.draw(c)
        c.restore()

        tp.color = Color.parseColor("#8A93B2")
        tp.textSize = 26f
        c.drawText("GIkoko One Recap", 60f, (H - 60).toFloat(), tp)

        if (watermark) {
            val wm = TextPaint(Paint.ANTI_ALIAS_FLAG)
            wm.color = Color.parseColor("#66FFC940")
            wm.textSize = 28f
            wm.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            c.drawText("GIkoko", (W - 160).toFloat(), 60f, wm)
        }

        val fo = FileOutputStream(outFile)
        fo.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }

    fun renderBranded(outFile: File, intro: Boolean, watermark: Boolean, vertical: Boolean = false) {
        val W = if (vertical) 720 else 1280
        val H = if (vertical) 1280 else 720
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#05070E"))

        val glow = Paint()
        glow.isAntiAlias = true
        glow.color = Color.parseColor("#263DE8F8")
        c.drawCircle((W / 2).toFloat(), (H / 2).toFloat(), 200f, glow)

        val ring = Paint()
        ring.isAntiAlias = true
        ring.color = Color.parseColor("#FFC940")
        ring.strokeWidth = 8f
        ring.style = Paint.Style.STROKE
        c.drawCircle((W / 2).toFloat(), (H / 2 - 60).toFloat(), 110f, ring)

        val tri = Paint()
        tri.isAntiAlias = true
        tri.color = Color.parseColor("#3DE8F8")
        c.drawCircle((W / 2).toFloat(), (H / 2 - 60).toFloat(), 82f, tri)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = Color.parseColor("#FFC940")
        tp.textSize = 64f
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        tp.textAlign = Paint.Align.CENTER
        if (intro) {
            c.drawText("GIkoko One Recap", (W / 2).toFloat(), (H / 2 + 160).toFloat(), tp)
            tp.color = Color.parseColor("#3DE8F8")
            tp.textSize = 40f
            c.drawText("AI VIDEO RECAP", (W / 2).toFloat(), (H / 2 + 230).toFloat(), tp)
        } else {
            c.drawText("Recap Complete", (W / 2).toFloat(), (H / 2 + 160).toFloat(), tp)
            tp.color = Color.parseColor("#8A93B2")
            tp.textSize = 36f
            c.drawText("GIkoko One Recap", (W / 2).toFloat(), (H / 2 + 230).toFloat(), tp)
        }

        if (watermark) {
            tp.color = Color.parseColor("#66FFC940")
            tp.textSize = 28f
            tp.textAlign = Paint.Align.RIGHT
            c.drawText("GIkoko", (W - 60).toFloat(), 60f, tp)
        }

        val fo = FileOutputStream(outFile)
        fo.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }
}
