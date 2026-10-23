package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.effect.Presentation
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
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CompressActivity : AppCompatActivity() {

    private var srcFile: File? = null
    private var outHeight = 720
    private var volGain = 1f
    private var busy = false

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var videoNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open compress")
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

    private fun topParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(10)
        return p
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
        ht.text = "VIDEO COMPRESS"
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
        statusText.text = "Video ကို 480p / 720p ချုံ့ပြီး file size လျှော့မယ် - share လွယ်ပါမယ်"
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

        val cr = card()
        cr.addView(sectionTitle("📏 RESOLUTION"))
        val rg = ChipGroup(this)
        rg.isSingleSelection = true
        rg.isSelectionRequired = true
        val resVals = intArrayOf(480, 720)
        listOf("480p (file အသေးဆုံး)", "720p").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 65000 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) outHeight = resVals[i] }
            rg.addView(ch)
        }
        rg.check(65001)
        cr.addView(rg)
        root.addView(cr)

        val cvo = card()
        cvo.addView(sectionTitle("🔊 VOLUME BOOST"))
        val vg = ChipGroup(this)
        vg.isSingleSelection = true
        vg.isSelectionRequired = true
        val volVals = floatArrayOf(1f, 2f, 3f)
        listOf("1x (မတိုး)", "2x", "3x").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 65100 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) volGain = volVals[i] }
            vg.addView(ch)
        }
        vg.check(65100)
        cvo.addView(vg)
        val nv = note("Volume 2x/3x က video အသံကို တိုးပေးမယ် - အသံ ကျဲနေတဲ့ video အတွက်ပါ။")
        nv.setPadding(0, dp(8), 0, 0)
        cvo.addView(nv)
        root.addView(cvo)

        val cc = card()
        val runBtn = flatBtn("🎞 COMPRESS လုပ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        cc.addView(runBtn)
        runBtn.setOnClickListener { run() }
        val nc = note("480p က file size တော်တော်လျှော့ပြီး ဖုန်းနည်းစနစ်တွေမှာ မြန်ပါတယ်။")
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
            val work = File(cacheDir, "compress")
            work.mkdirs()
            val dst = File(work, "cp_src." + extOf(data.data!!))
            if (copyUri(data.data!!, dst)) {
                srcFile = dst
                val dur = mediaDurationMs(dst) / 1000L
                videoNameText.text = dst.name + "  -  " + dur + " sec"
                Toast.makeText(this, "Video ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Video ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
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
        busy = true
        progressBar.visibility = View.VISIBLE
        statusText.text = "ချုံ့နေသည်..."
        lifecycleScope.launch {
            try {
                val vol = volGain
                val aps: List<AudioProcessor> = if (vol > 1f) ImmutableList.of(mixOf(vol)) else ImmutableList.of()
                val fx: List<Effect> = ImmutableList.of<Effect>(Presentation.createForHeight(outHeight))
                val mi = MediaItem.Builder().setUri(Uri.fromFile(video)).build()
                val item = EditedMediaItem.Builder(mi).setEffects(Effects(aps, fx)).build()
                val seq = EditedMediaItemSequence(ImmutableList.of(item))
                val out = File(cacheDir, "compress_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seq).build(), out) }
                val name = "COMP_" + System.currentTimeMillis() + ".mp4"
                val ok = DownloadsUtil.saveFile(this@CompressActivity, out, name, "video/mp4")
                busy = false
                progressBar.visibility = View.GONE
                if (ok) {
                    statusText.text = "ပြီးပါပြီ - Downloads မှာ " + name
                    Toast.makeText(this@CompressActivity, "ပြီးပါပြီ - Downloads မှာ " + name, Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "သိမ်းရာမှာ မအောင်မြင်ပါ"
                }
            } catch (e: Exception) {
                busy = false
                progressBar.visibility = View.GONE
                statusText.text = "မအောင်မြင်: " + (e.message ?: "")
                Toast.makeText(this@CompressActivity, "မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mixOf(vol: Float): ChannelMixingAudioProcessor {
        val mix = ChannelMixingAudioProcessor()
        mix.putChannelMixingMatrix(ChannelMixingMatrix(1, 1, floatArrayOf(vol)))
        mix.putChannelMixingMatrix(ChannelMixingMatrix(2, 2, floatArrayOf(vol, 0f, 0f, vol)))
        mix.putChannelMixingMatrix(ChannelMixingMatrix(6, 6, floatArrayOf(
            vol, 0f, 0f, 0f, 0f, 0f,
            0f, vol, 0f, 0f, 0f, 0f,
            0f, 0f, vol, 0f, 0f, 0f,
            0f, 0f, 0f, vol, 0f, 0f,
            0f, 0f, 0f, 0f, vol, 0f,
            0f, 0f, 0f, 0f, 0f, vol)))
        return mix
    }
}
