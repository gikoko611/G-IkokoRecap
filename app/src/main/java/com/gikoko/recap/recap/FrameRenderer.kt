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

    class TH(val accent: Int, val glow: Int, val grid: Int)

    fun themeColors(theme: String): TH {
        return when (theme) {
            "blue" -> TH(Color.parseColor("#4DA3FF"), Color.parseColor("#7EE0FF"), Color.parseColor("#0D2049"))
            "purple" -> TH(Color.parseColor("#C77DFF"), Color.parseColor("#9AE6FF"), Color.parseColor("#241348"))
            "green" -> TH(Color.parseColor("#3DDC84"), Color.parseColor("#A5F3C4"), Color.parseColor("#0F3D2E"))
            "red" -> TH(Color.parseColor("#FF5C5C"), Color.parseColor("#FFB4A2"), Color.parseColor("#3D1414"))
            else -> TH(Color.parseColor("#FFC940"), Color.parseColor("#3DE8F8"), Color.parseColor("#13224A"))
        }
    }

    private fun drawWatermark(c: Canvas, W: Int, H: Int, on: Boolean, wmSize: Int, wmPos: String, wmLogo: String, accent: Int) {
        if (!on) return
        val scale = if (wmSize <= 0) 1.0 else wmSize / 100.0
        if (wmLogo.isNotBlank()) {
            val lf = File(wmLogo)
            if (lf.exists()) {
                runCatching {
                    val bm = android.graphics.BitmapFactory.decodeFile(wmLogo)
                    if (bm != null && bm.width > 0) {
                        val targetW = (140.0 * scale).toInt().coerceIn(30, W / 3)
                        val targetH = (targetW * bm.height / bm.width).coerceAtMost(H / 3)
                        val scaled = Bitmap.createScaledBitmap(bm, targetW, targetH, true)
                        val margin = 40f
                        val x = if (wmPos == "tl" || wmPos == "bl") margin else (W - margin - scaled.width).toFloat()
                        val y = if (wmPos == "tl" || wmPos == "tr") margin else (H - margin - scaled.height).toFloat()
                        c.drawBitmap(scaled, x, y, null)
                    }
                }
                return
            }
        }
        val wm = TextPaint(Paint.ANTI_ALIAS_FLAG)
        wm.color = (accent and 0x00FFFFFF) or 0x66000000
        wm.textSize = (28 * scale).toFloat()
        wm.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        if (wmPos == "tl" || wmPos == "bl") {
            wm.textAlign = Paint.Align.LEFT
            c.drawText("GIkoko", 60f, if (wmPos == "tl") 60f else (H - 60).toFloat(), wm)
        } else {
            wm.textAlign = Paint.Align.RIGHT
            c.drawText("GIkoko", (W - 60).toFloat(), if (wmPos == "tr") 60f else (H - 60).toFloat(), wm)
        }
    }

    fun render(title: String, text: String, index: Int, outFile: File, watermark: Boolean, vertical: Boolean = false, wmSize: Int = 100, wmPos: String = "tr", wmLogo: String = "", theme: String = "gold") {
        val W = if (vertical) 720 else 1280
        val H = if (vertical) 1280 else 720
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#05070E"))
        val th = themeColors(theme)

        val grid = Paint()
        grid.isAntiAlias = true
        grid.color = th.grid
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
        border.color = th.accent
        border.strokeWidth = 6f
        border.style = Paint.Style.STROKE
        c.drawRect(20f, 20f, (W - 20).toFloat(), (H - 20).toFloat(), border)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = th.glow
        tp.textSize = 44f
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        if (title.length > 40) {
            c.drawText(title.substring(0, 40), 60f, 100f, tp)
        } else {
            c.drawText(title, 60f, 100f, tp)
        }

        tp.color = th.accent
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

        drawWatermark(c, W, H, watermark, wmSize, wmPos, wmLogo, th.accent)

        val fo = FileOutputStream(outFile)
        fo.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }

    fun renderBranded(outFile: File, intro: Boolean, watermark: Boolean, vertical: Boolean = false, wmSize: Int = 100, wmPos: String = "tr", wmLogo: String = "", theme: String = "gold") {
        val W = if (vertical) 720 else 1280
        val H = if (vertical) 1280 else 720
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#05070E"))
        val th = themeColors(theme)

        val glow = Paint()
        glow.isAntiAlias = true
        glow.color = (th.glow and 0x00FFFFFF) or 0x26000000
        c.drawCircle((W / 2).toFloat(), (H / 2).toFloat(), 200f, glow)

        val ring = Paint()
        ring.isAntiAlias = true
        ring.color = th.accent
        ring.strokeWidth = 8f
        ring.style = Paint.Style.STROKE
        c.drawCircle((W / 2).toFloat(), (H / 2 - 60).toFloat(), 110f, ring)

        val tri = Paint()
        tri.isAntiAlias = true
        tri.color = th.glow
        c.drawCircle((W / 2).toFloat(), (H / 2 - 60).toFloat(), 82f, tri)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = th.accent
        tp.textSize = 64f
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        tp.textAlign = Paint.Align.CENTER
        if (intro) {
            c.drawText("GIkoko One Recap", (W / 2).toFloat(), (H / 2 + 160).toFloat(), tp)
            tp.color = th.glow
            tp.textSize = 40f
            c.drawText("AI VIDEO RECAP", (W / 2).toFloat(), (H / 2 + 230).toFloat(), tp)
        } else {
            c.drawText("Recap Complete", (W / 2).toFloat(), (H / 2 + 160).toFloat(), tp)
            tp.color = Color.parseColor("#8A93B2")
            tp.textSize = 36f
            c.drawText("GIkoko One Recap", (W / 2).toFloat(), (H / 2 + 230).toFloat(), tp)
        }

        drawWatermark(c, W, H, watermark, wmSize, wmPos, wmLogo, th.accent)

        val fo = FileOutputStream(outFile)
        fo.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }
}