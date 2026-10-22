package com.gikoko.recap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.gikoko.recap.recap.DownloadsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WatermarkActivity : AppCompatActivity() {

    private var srcFile: File? = null
    private var frameBmp: Bitmap? = null
    private var frameW = 1280
    private var frameH = 720
    private var regionIdx = 1
    private var sizeIdx = 1
    private var modeIdx = 0
    private var exporting = false

    private var wmDone: ((ExportException?) -> Unit)? = null

    private lateinit var preview: ImageView
    private lateinit var statusText: TextView
    private lateinit var pickBtn: Button
    private lateinit var startBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open watermark remover")
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    private fun sectionTitle(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 13f
        tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD), Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#3DE8F8"))
        tv.letterSpacing = 0.15f
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.bottomMargin = dp(10)
        tv.layoutParams = p
        return tv
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setBackgroundResource(R.drawable.bg_card)
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(dp(16), dp(10), dp(16), dp(10))
        l.layoutParams = p
        l.setPadding(dp(16), dp(16), dp(16), dp(16))
        return l
    }

    private fun note(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#8A93B2"))
        tv.textSize = 11f
        return tv
    }

    private fun flatBtn(text: String, bg: Int, color: String): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 13f
        b.setTextColor(Color.parseColor(color))
        b.setBackgroundResource(bg)
        return b
    }

    private fun chipOf(text: String): com.google.android.material.chip.Chip {
        val c = com.google.android.material.chip.Chip(this)
        c.text = text
        c.isCheckable = true
        c.isClickable = true
        c.chipBackgroundColor = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#2A2410"), Color.parseColor("#10182C"))
        )
        c.chipStrokeWidth = dp(1).toFloat()
        c.chipStrokeColor = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#FFC940"), Color.parseColor("#223055"))
        )
        c.setTextColor(android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#FFC940"), Color.parseColor("#C9D2EC"))
        ))
        return c
    }

    private fun build(root: LinearLayout) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val hp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        hp.setMargins(dp(8), dp(16), dp(8), dp(4))
        root.addView(head, hp)

        val back = TextView(this)
        back.text = "‹"
        back.textSize = 30f
        back.setTextColor(Color.parseColor("#FFC940"))
        back.setPadding(dp(16), dp(4), dp(16), dp(4))
        back.setOnClickListener { finish() }
        head.addView(back)

        val ht = TextView(this)
        ht.text = "WATERMARK REMOVER"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c1 = card()
        c1.addView(sectionTitle("① VIDEO ရွေးမည်"))
        pickBtn = flatBtn("📁 PICK VIDEO", R.drawable.bg_chip_selected, "#F2F5FF")
        c1.addView(pickBtn)
        pickBtn.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("video/*"), 88)
        }
        preview = ImageView(this)
        preview.visibility = View.GONE
        preview.adjustViewBounds = true
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pp.topMargin = dp(10)
        c1.addView(preview, pp)
        val pvNote = note("🔎 အဝါရောင် box က watermark ရှိတဲ့ နေရာကို ပြတာပါ")
        pvNote.setPadding(0, dp(6), 0, 0)
        c1.addView(pvNote)
        root.addView(c1)

        val c2 = card()
        c2.addView(sectionTitle("② WATERMARK နေရာ"))
        val rg = com.google.android.material.chip.ChipGroup(this)
        rg.isSingleSelection = true
        rg.isSelectionRequired = true
        listOf("TOP LEFT", "TOP RIGHT", "BOTTOM LEFT", "BOTTOM RIGHT", "CENTER").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 70000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    regionIdx = i
                    showPreview()
                }
            }
            rg.addView(ch)
        }
        rg.check(70000 + regionIdx)
        c2.addView(rg)

        c2.addView(sectionTitle("③ SIZE"))
        val sg = com.google.android.material.chip.ChipGroup(this)
        sg.isSingleSelection = true
        sg.isSelectionRequired = true
        listOf("S", "M", "L").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 70100 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    sizeIdx = i
                    showPreview()
                }
            }
            sg.addView(ch)
        }
        sg.check(70100 + sizeIdx)
        c2.addView(sg)

        c2.addView(sectionTitle("④ MODE"))
        val mg = com.google.android.material.chip.ChipGroup(this)
        mg.isSingleSelection = true
        mg.isSelectionRequired = true
        listOf("BLUR PATCH", "DARK PATCH", "CROP").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 70200 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) modeIdx = i
            }
            mg.addView(ch)
        }
        mg.check(70200 + modeIdx)
        c2.addView(mg)

        val modeNote = note("BLUR PATCH = watermark နေရာကို မှုန်ဝါး (mosaic) ကွက် ခင်းတာပါ။ DARK PATCH = အနက်ကွက် ခင်းတာပါ။ CROP = watermark ပါတဲ့ အနားဘက်ကို ဖြတ်ပစ်တာပါ (video အရွယ်အစား ငယ်သွားမယ်၊ CENTER နဲ့ တွဲမရပါ)။ AI auto-remove မဟုတ်ပါ - corner/edge watermark တွေအတွက် အသုံးဝင်ဆုံးပါတယ်။ 5 မိနစ်အောက် video တွေမှာ အဆင်ပြေဆုံးပါတယ်။")
        modeNote.setPadding(0, dp(8), 0, 0)
        c2.addView(modeNote)
        root.addView(c2)

        val c3 = card()
        startBtn = flatBtn("🎬 START REMOVAL", R.drawable.bg_gold_btn, "#0A0A00")
        c3.addView(startBtn)
        startBtn.setOnClickListener { startRemoval() }
        statusText = TextView(this)
        statusText.setTextColor(Color.parseColor("#3DE8F8"))
        statusText.textSize = 12f
        statusText.setPadding(0, dp(8), 0, 0)
        c3.addView(statusText)
        root.addView(c3)

        val pad = TextView(this)
        val padp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, padp)
    }

    private fun patchRect(): RectF {
        val w = floatArrayOf(0.30f, 0.45f, 0.60f)[sizeIdx]
        val h = 0.14f
        val m = 0.02f
        return when (regionIdx) {
            0 -> RectF(m, m, m + w, m + h)
            1 -> RectF(1f - m - w, m, 1f - m, m + h)
            2 -> RectF(m, 1f - m - h, m + w, 1f - m)
            3 -> RectF(1f - m - w, 1f - m - h, 1f - m, 1f - m)
            else -> RectF((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
        }
    }

    private fun showPreview() {
        val f = frameBmp ?: return
        val bmp = f.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        val r = patchRect()
        val rect = RectF(r.left * bmp.width, r.top * bmp.height, r.right * bmp.width, r.bottom * bmp.height)
        val fill = Paint()
        fill.color = Color.argb(70, 255, 201, 64)
        c.drawRect(rect, fill)
        val stroke = Paint()
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 8f
        stroke.color = Color.parseColor("#FFC940")
        c.drawRect(rect, stroke)
        preview.setImageBitmap(bmp)
    }

    private fun loadFrame() {
        val src = srcFile ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(src.absolutePath)
                frameW = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                frameH = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                val bmp = mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                mmr.release()
                withContext(Dispatchers.Main) {
                    frameBmp = bmp
                    preview.visibility = View.VISIBLE
                    showPreview()
                    val mb = src.length() / (1024.0 * 1024.0)
                    statusText.text = "Video အသင့် ✓  " + frameW + "x" + frameH + "  -  " + String.format("%.1f", mb) + " MB"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Video ဖတ်မရပါ: " + (e.message ?: "")
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 88 && resultCode == -1 && data?.data != null) {
            runCatching {
                val dst = File(cacheDir, "wm_src.mp4")
                if (dst.exists()) dst.delete()
                contentResolver.openInputStream(data.data!!)?.use { ins ->
                    dst.outputStream().use { ins.copyTo(it) }
                }
                if (dst.length() > 0L) {
                    srcFile = dst
                    loadFrame()
                }
            }.onFailure {
                Toast.makeText(this, "Video ဖွင့်မရပါ: " + (it.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildOverlayBitmap(): Bitmap? {
        val f = frameBmp ?: return null
        if (frameW <= 0 || frameH <= 0) return null
        var scale = 1f
        val maxDim = maxOf(frameW, frameH)
        if (maxDim > 1920) scale = 1920f / maxDim
        val ow = (frameW * scale).toInt().coerceAtLeast(8)
        val oh = (frameH * scale).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = patchRect()
        val rect = RectF(r.left * ow, r.top * oh, r.right * ow, r.bottom * oh)
        if (modeIdx == 0) {
            val sx = (r.left * f.width).toInt().coerceIn(0, f.width - 4)
            val sy = (r.top * f.height).toInt().coerceIn(0, f.height - 4)
            val sw = (r.width() * f.width).toInt().coerceIn(4, f.width - sx)
            val sh = (r.height() * f.height).toInt().coerceIn(4, f.height - sy)
            val patch = Bitmap.createBitmap(f, sx, sy, sw, sh)
            val tiny = Bitmap.createScaledBitmap(patch, 14, 10, true)
            val mosaic = Bitmap.createScaledBitmap(tiny, rect.width().toInt().coerceAtLeast(2), rect.height().toInt().coerceAtLeast(2), false)
            c.drawBitmap(mosaic, null, rect, Paint(Paint.FILTER_BITMAP))
        } else {
            val p = Paint()
            p.color = Color.argb(215, 10, 12, 20)
            c.drawRoundRect(rect, dp(8).toFloat(), dp(8).toFloat(), p)
        }
        return bmp
    }

    private fun startRemoval() {
        val src = srcFile
        if (src == null || !src.exists()) {
            Toast.makeText(this, "PICK VIDEO နှိပ်ပြီး video ရွေးပါ", Toast.LENGTH_LONG).show()
            return
        }
        if (modeIdx == 2 && regionIdx == 4) {
            Toast.makeText(this, "CROP mode မှာ CENTER ရွေးလို့မရပါ - corner တစ်ခုခု ရွေးပါ", Toast.LENGTH_LONG).show()
            return
        }
        if (exporting) return
        exporting = true
        startBtn.isEnabled = false
        pickBtn.isEnabled = false
        statusText.text = "Video ဖျတ်နေသည်..."
        DebugLog.log(this, "wm removal start region=" + regionIdx + " size=" + sizeIdx + " mode=" + modeIdx)
        lifecycleScope.launch {
            try {
                val out = File(cacheDir, "wm_out.mp4")
                if (out.exists()) out.delete()
                withContext(Dispatchers.Main) { export(src, out) }
                val ok = DownloadsUtil.saveFile(this@WatermarkActivity, out, "GIkokoWmRemoved_" + System.currentTimeMillis() + ".mp4", "video/mp4")
                statusText.text = if (ok) "ပြီးပါပြီ ✓ - Downloads ထဲမှာ သိမ်းပြီးပါပြီ" else "ပြီးပါပြီ ✓ (Downloads သိမ်းမှု မအောင်မြင်)"
                Toast.makeText(this@WatermarkActivity, if (ok) "Downloads ထဲမှာ သိမ်းပြီးပါပြီ" else "Export ပြီးသား - Downloads သိမ်းမှု မအောင်မြင်", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                DebugLog.log(this@WatermarkActivity, "wm removal failed: " + (e.message ?: ""))
                statusText.text = "မအောင်မြင်ပါ: " + (e.message ?: "")
                Toast.makeText(this@WatermarkActivity, "မအောင်မြင်ပါ - 🐞 Crash Log (TOOLS) ကို စစ်ပါ", Toast.LENGTH_LONG).show()
            } finally {
                exporting = false
                startBtn.isEnabled = true
                pickBtn.isEnabled = true
            }
        }
    }

    private suspend fun export(src: File, out: File) {
        val videoEffects = ArrayList<Effect>()
        if (modeIdx == 2) {
            val r = patchRect()
            var cropL = 0f
            var cropR = 0f
            var cropT = 0f
            var cropB = 0f
            when (regionIdx) {
                0 -> { cropL = r.right; cropT = r.bottom }
                1 -> { cropR = 1f - r.left; cropT = r.bottom }
                2 -> { cropL = r.right; cropB = 1f - r.top }
                else -> { cropR = 1f - r.left; cropB = 1f - r.top }
            }
            videoEffects.add(Crop(cropL, cropR, cropT, cropB))
        } else {
            val ob = buildOverlayBitmap()
            if (ob != null) {
                val overlay: TextureOverlay = BitmapOverlay.createStaticBitmapOverlay(ob)
                videoEffects.add(OverlayEffect(ImmutableList.of(overlay)))
            }
        }
        val effects = Effects(ImmutableList.of(), ImmutableList.copyOf(videoEffects))
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(src))).setEffects(effects).build()
        val seq = EditedMediaItemSequence(ImmutableList.of(item))
        val composition = Composition.Builder(seq).build()
        val transformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build()
        var done = false
        val holder = ProgressHolder()
        lifecycleScope.launch {
            while (!done) {
                val st = transformer.getProgress(holder)
                if (st == Transformer.PROGRESS_STATE_AVAILABLE) {
                    statusText.text = "ဖျတ်နေသည်... " + holder.progress.toInt() + "%"
                }
                delay(700)
            }
        }
        suspendCancellableCoroutine { cont ->
            wmDone = { err: ExportException? ->
                wmDone = null
                done = true
                if (err == null) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    if (cont.isActive) cont.resumeWithException(err)
                }
            }
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    wmDone?.invoke(null)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    wmDone?.invoke(exportException)
                }
            })
            transformer.start(composition, out.absolutePath)
            cont.invokeOnCancellation {
                done = true
                runCatching { transformer.cancel() }
            }
        }
    }
}
