package com.gikoko.recap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.gikoko.recap.recap.DownloadsUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WmBurnActivity : AppCompatActivity() {

    private var srcFile: File? = null
    private var logoFile: File? = null
    private var wmPct = 14
    private var wmPos = 7
    private var busy = false

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var videoNameText: TextView
    private lateinit var logoNameText: TextView
    private lateinit var textInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open wm burn")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    private fun chipOf(text: String): Chip {
        val c = Chip(this)
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

    private fun flatBtn(text: String, bg: Int, color: String): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 13f
        b.setTextColor(Color.parseColor(color))
        b.setBackgroundResource(bg)
        return b
    }

    private fun build(root: LinearLayout) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val hp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        hp.setMargins(dp(8), dp(16), dp(8), dp(4))
        root.addView(head, hp)

        val back = TextView(this)
        back.text = "\u2039"
        back.textSize = 30f
        back.setTextColor(Color.parseColor("#FFC940"))
        back.setPadding(dp(16), dp(4), dp(16), dp(4))
        back.setOnClickListener { finish() }
        head.addView(back)

        val ht = TextView(this)
        ht.text = "WM / LOGO BURN"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val cs = card()
        progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        progressBar.visibility = View.GONE
        cs.addView(progressBar)
        statusText = TextView(this)
        statusText.setTextColor(Color.parseColor("#8A93B2"))
        statusText.textSize = 12f
        statusText.setPadding(0, dp(6), 0, 0)
        statusText.text = "Video ပေါ်မှာ watermark စာ (ဒါမှမဟုတ်) logo ကို အမာခံ burn ထည့်ပေးမယ် - size နဲ့ နေရာ ရွေးလို့ရပါတယ်။"
        cs.addView(statusText)
        root.addView(cs)

        val cv = card()
        cv.addView(sectionTitle("📹 SOURCE VIDEO"))
        val pickV = flatBtn("📁 VIDEO ရွေးမည်", R.drawable.bg_gold_btn, "#0A0A00")
        cv.addView(pickV)
        pickV.setOnClickListener { pickVideo() }
        videoNameText = note("Video မရွေးရသေးပါ")
        videoNameText.setPadding(0, dp(8), 0, 0)
        cv.addView(videoNameText)
        root.addView(cv)

        val ct = card()
        ct.addView(sectionTitle("✍ WATERMARK စာ"))
        textInput = EditText(this)
        textInput.hint = "ဥပမာ - @your_channel"
        textInput.isSingleLine = true
        textInput.textSize = 13f
        textInput.setTextColor(Color.parseColor("#F2F5FF"))
        textInput.setHintTextColor(Color.parseColor("#8A93B2"))
        textInput.setBackgroundResource(R.drawable.bg_input)
        textInput.setPadding(dp(12), dp(10), dp(12), dp(10))
        ct.addView(textInput)
        val nt = note("မထည့်လည်းရပါတယ် - Logo တစ်ခုတည်း နဲ့လည်း ရပါတယ်။")
        nt.setPadding(0, dp(8), 0, 0)
        ct.addView(nt)
        root.addView(ct)

        val cl = card()
        cl.addView(sectionTitle("🖼 LOGO"))
        val pickL = flatBtn("🖼 LOGO ရွေးမည်", R.drawable.bg_gold_btn, "#0A0A00")
        cl.addView(pickL)
        pickL.setOnClickListener { pickLogo() }
        logoNameText = note("Logo မရွေးရသေးပါ")
        logoNameText.setPadding(0, dp(8), 0, 0)
        cl.addView(logoNameText)
        root.addView(cl)

        val csize = card()
        csize.addView(sectionTitle("📏 SIZE"))
        val sg = ChipGroup(this)
        sg.isSingleSelection = true
        sg.isSelectionRequired = true
        val pctVals = intArrayOf(8, 14, 20)
        listOf("8% (သေး)", "14%", "20% (ကြီး)").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 66000 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) wmPct = pctVals[i] }
            sg.addView(ch)
        }
        sg.check(66001)
        csize.addView(sg)
        val ns = note("Size % က video အနံရဲ့ % အလိုက် ဖြစ်မယ်။")
        ns.setPadding(0, dp(8), 0, 0)
        csize.addView(ns)
        root.addView(csize)

        val cp = card()
        cp.addView(sectionTitle("📍 POSITION"))
        val pg = ChipGroup(this)
        pg.isSingleSelection = true
        pg.isSelectionRequired = true
        listOf("↖ TL", "↑ TC", "↗ TR", "← ML", "■ MC", "→ MR", "↙ BL", "↓ BC", "↘ BR").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 66100 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) wmPos = i }
            pg.addView(ch)
        }
        pg.check(66107)
        cp.addView(pg)
        root.addView(cp)

        val cc = card()
        val runBtn = flatBtn("🔥 BURN လုပ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        cc.addView(runBtn)
        runBtn.setOnClickListener { run() }
        val nc = note("Merge မလုပ်ခင် ဒါမှမဟုတ် merge ပြီး video - ဘယ် video မဆို သုံးလို့ရပါတယ်။")
        nc.setPadding(0, dp(8), 0, 0)
        cc.addView(nc)
        root.addView(cc)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
    }

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.setType("video/*")
        startActivityForResult(i, 90)
    }

    private fun pickLogo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.setType("image/*")
        startActivityForResult(i, 91)
    }

    private fun extOf(uri: Uri): String {
        return try {
            val n = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val dot = n?.lastIndexOf('.')
            if (n != null && dot != null && dot < n.length - 1) n.substring(dot + 1).lowercase().take(5) else "mp4"
        } catch (e: Exception) {
            "mp4"
        }
    }

    private fun copyUri(uri: Uri, dst: File): Boolean {
        return try {
            dst.delete()
            contentResolver.openInputStream(uri)?.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
            dst.length() > 0L
        } catch (e: Exception) {
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 90 && resultCode == -1 && data?.data != null) {
            val work = File(cacheDir, "wmburn")
            work.mkdirs()
            val dst = File(work, "wm_src." + extOf(data.data!!))
            if (copyUri(data.data!!, dst)) {
                srcFile = dst
                val dur = mediaDurationMs(dst) / 1000L
                videoNameText.text = dst.name + "  -  " + dur + " sec"
                Toast.makeText(this, "Video ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Video ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
            }
        }
        if (requestCode == 91 && resultCode == -1 && data?.data != null) {
            val work = File(cacheDir, "wmburn")
            work.mkdirs()
            val dst = File(work, "wm_logo.png")
            if (copyUri(data.data!!, dst)) {
                logoFile = dst
                logoNameText.text = dst.name + " ✓"
                Toast.makeText(this, "Logo ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Logo ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mediaDurationMs(f: File): Long {
        var d = 0L
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(f.absolutePath)
            d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
        } catch (e: Exception) {
        }
        return d
    }

    private fun videoSize(f: File): IntArray {
        val r = intArrayOf(0, 0)
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(f.absolutePath)
            r[0] = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            r[1] = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            mmr.release()
        } catch (e: Exception) {
        }
        return r
    }

    private fun decodeScaled(f: File, targetW: Int): Bitmap? {
        return try {
            val o1 = BitmapFactory.Options()
            o1.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, o1)
            if (o1.outWidth <= 0 || o1.outHeight <= 0) return null
            var sample = 1
            while (o1.outWidth / (sample * 2) >= 1024) sample *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = sample
            val src = BitmapFactory.decodeFile(f.absolutePath, o2) ?: return null
            val h = (src.height * targetW.toFloat() / src.width).toInt().coerceAtLeast(1)
            val outBmp = Bitmap.createScaledBitmap(src, targetW, h, true)
            if (outBmp != src) src.recycle()
            outBmp
        } catch (e: Exception) {
            null
        }
    }

    private fun buildOverlayBitmap(vw: Int, vh: Int, txt: String): Bitmap {
        var s = 1f
        val maxDim = maxOf(vw, vh)
        if (maxDim > 1280) s = 1280f / maxDim
        val bw = (vw * s).toInt().coerceAtLeast(16)
        val bh = (vh * s).toInt().coerceAtLeast(16)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val margin = (bw * 0.03f).toInt()
        val col = wmPos % 3
        val row = wmPos / 3
        var logoH = 0
        logoFile?.let { lf ->
            if (lf.exists()) {
                val targetW = (bw * wmPct / 100f).toInt().coerceAtLeast(12)
                val lb = decodeScaled(lf, targetW)
                if (lb != null) {
                    val x = when (col) { 0 -> margin; 1 -> (bw - lb.width) / 2; else -> bw - lb.width - margin }
                    val y = when (row) { 0 -> margin; 1 -> (bh - lb.height) / 2; else -> bh - lb.height - margin }
                    c.drawBitmap(lb, x.toFloat(), y.toFloat(), null)
                    logoH = lb.height + margin / 2
                    lb.recycle()
                }
            }
        }
        if (txt.isNotEmpty()) {
            val tp = (bh * (wmPct / 100f) * 0.55f).coerceAtLeast(10f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.textSize = tp
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.color = Color.parseColor("#FFFFFF")
            paint.setShadowLayer(tp / 8f, 0f, tp / 20f, Color.parseColor("#99000000"))
            val tw = paint.measureText(txt)
            val asc = -paint.ascent()
            val x = when (col) { 0 -> margin.toFloat(); 1 -> (bw - tw) / 2f; else -> bw - tw - margin.toFloat() }
            val y = when (row) {
                0 -> margin + logoH + asc
                1 -> (bh + asc) / 2f + logoH / 2f
                else -> bh - margin.toFloat() - paint.descent() - logoH
            }
            c.drawText(txt, x, y, paint)
        }
        return bmp
    }

    private var exportDone: ((Exception?) -> Unit)? = null

    private suspend fun export(composition: Composition, out: File) {
        if (out.exists()) out.delete()
        val transformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    exportDone?.invoke(null)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    exportDone?.invoke(exportException)
                }
            })
            .build()
        suspendCancellableCoroutine { cont ->
            exportDone = { err: Exception? ->
                exportDone = null
                if (err == null) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    if (cont.isActive) cont.resumeWithException(err)
                }
            }
            transformer.start(composition, out.absolutePath)
            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }
        }
    }

    private fun run() {
        if (busy) {
            Toast.makeText(this, "လုပ်နေသေးသည် - ခဏစောင့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val video = srcFile
        if (video == null || !video.exists()) {
            Toast.makeText(this, "အရင်ဆုံ VIDEO ရွေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val txt = textInput.text.toString().trim()
        val logo = logoFile
        if (txt.isEmpty() && (logo == null || !logo.exists())) {
            Toast.makeText(this, "စာ (ဒါမှမဟုတ်) logo တစ်ခုခု ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        progressBar.visibility = View.VISIBLE
        statusText.text = "ထည့်နေသည်..."
        lifecycleScope.launch {
            try {
                val size = withContext(Dispatchers.IO) { videoSize(video) }
                if (size[0] <= 0 || size[1] <= 0) throw IllegalStateException("video size မရနိုင်ပါ")
                val bmp = withContext(Dispatchers.IO) { buildOverlayBitmap(size[0], size[1], txt) }
                val fx: List<Effect> = ImmutableList.of<Effect>(OverlayEffect(ImmutableList.of(BitmapOverlay.createStaticBitmapOverlay(bmp))))
                val mi = MediaItem.Builder().setUri(Uri.fromFile(video)).build()
                val item = EditedMediaItem.Builder(mi).setEffects(Effects(ImmutableList.of(), fx)).build()
                val seq = EditedMediaItemSequence(ImmutableList.of(item))
                val out = File(cacheDir, "wmburn_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seq).build(), out) }
                runCatching { bmp.recycle() }
                val name = "WMADD_" + System.currentTimeMillis() + ".mp4"
                val ok = DownloadsUtil.saveFile(this@WmBurnActivity, out, name, "video/mp4")
                busy = false
                progressBar.visibility = View.GONE
                if (ok) {
                    statusText.text = "ပြီးပါပြီ - Downloads မှာ " + name
                    Toast.makeText(this@WmBurnActivity, "ပြီးပါပြီ - Downloads မှာ " + name, Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "သိမ်းရာမှာ မအောင်မြင်ပါ"
                }
            } catch (e: Exception) {
                busy = false
                progressBar.visibility = View.GONE
                statusText.text = "မအောင်မြင်: " + (e.message ?: "")
                Toast.makeText(this@WmBurnActivity, "မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}
