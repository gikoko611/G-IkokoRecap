package com.gikoko.recap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.gikoko.recap.recap.DownsloadsUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SlideshowActivity : AppCompatActivity() {

    private val slides = ArrayList<File>()
    private var audioFile: File? = null
    private var secPer = 3
    private var bgmVol = 0.4f
    private var busy = false

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var audioNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open slideshow")
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
        ht.text = "PHOTO SLIDESHOW"
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
        statusText.text = "ဓာတ်ပုံတွေ ရွေးပြီး slideshow video တစ်ခု ဖန်တီးပါ - BGM ထည့်လို့ရပါတယ်"
        cs.addView(statusText)
        root.addView(cs)

        val cp = card()
        cp.addView(sectionTitle("🖼 ဓာတ်ပုံများ ရွေးပါ"))
        val pickBtn = flatBtn("🖼 ဓာတ်ပုံ ရွေးမည် (များများရွေးလို့ရ)", R.drawable.bg_gold_btn, "#0A0A00")
        cp.addView(pickBtn)
        pickBtn.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.setType("image/*")
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(i, 90)
        }
        countText = note("ရွေးထားတဲ့ ပုံ: 0")
        countText.setPadding(0, dp(8), 0, 0)
        cp.addView(countText)
        val clearBtn = flatBtn("✕ အားလုံး ဖျက်မည်", R.drawable.bg_input, "#FF5C5C")
        cp.addView(clearBtn, topParams())
        clearBtn.setOnClickListener {
            slides.clear()
            countText.text = "ရွေးထားတဲ့ ပုံ: 0"
            Toast.makeText(this, "ဖျက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
        }
        root.addView(cp)

        val cd = card()
        cd.addView(sectionTitle("⏱ တစ်ပုံလျှင် ကြာချိန်"))
        val dg = ChipGroup(this)
        dg.isSingleSelection = true
        dg.isSelectionRequired = true
        val secVals = intArrayOf(2, 3, 5)
        listOf("2 sec", "3 sec", "5 sec").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 64000 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) secPer = secVals[i] }
            dg.addView(ch)
        }
        dg.check(64001)
        cd.addView(dg)
        root.addView(cd)

        val cb = card()
        cb.addView(sectionTitle("🎵 BGM (ချန်လို့ရ)"))
        val pickA = flatBtn("🎵 BGM AUDIO ရွေးမည်", R.drawable.bg_chip_selected, "#F2F5FF")
        cb.addView(pickA)
        pickA.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.setType("audio/*")
            startActivityForResult(i, 91)
        }
        audioNameText = note("Audio မရွေးရသေးပါ - မရွေးရင် အသံမပါတဲ့ slideshow ဖြစ်မယ်")
        audioNameText.setPadding(0, dp(8), 0, 0)
        cb.addView(audioNameText)
        cb.addView(sectionTitle("BGM VOLUME"))
        val vg = ChipGroup(this)
        vg.isSingleSelection = true
        vg.isSelectionRequired = true
        val volVals = floatArrayOf(0.2f, 0.4f, 0.6f)
        listOf("20%", "40%", "60%").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 64200 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) bgmVol = volVals[i] }
            vg.addView(ch)
        }
        vg.check(64201)
        cb.addView(vg, topParams())
        root.addView(cb)

        val cr = card()
        val runBtn = flatBtn("🎬 SLIDESHOW ထုတ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        cr.addView(runBtn)
        runBtn.setOnClickListener { run() }
        val nr = note("အများဆုံး ပုံ 30 ပုံ - 720p အဖြစ် ချုံ့ပြီး SLIDE_ နာမည်နဲ့ Downloads မှာ သိမ်းပါမယ်။")
        nr.setPadding(0, dp(8), 0, 0)
        cr.addView(nr)
        root.addView(cr)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
    }

    private fun extOf(uri: Uri): String {
        return try {
            val n = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val dot = n?.lastIndexOf('.')
            if (n != null && dot != null && dot < n.length - 1) n.substring(dot + 1).lowercase().take(5) else "img"
        } catch (e: Exception) {
            "img"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != -1 || data == null) return
        if (requestCode == 90) {
            val work = File(cacheDir, "slides")
            work.mkdirs()
            val uris = ArrayList<Uri>()
            val cd = data.clipData
            if (cd != null) {
                for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri)
            } else if (data.data != null) {
                uris.add(data.data!!)
            }
            if (uris.isEmpty()) return
            var ok = 0
            val stamp = System.currentTimeMillis()
            for ((k, u) in uris.withIndex()) {
                if (k >= 30) break
                val dst = File(work, "slide_" + stamp + "_" + k + "." + extOf(u))
                val copied = try {
                    dst.delete()
                    contentResolver.openInputStream(u)?.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                    dst.length() > 0L
                } catch (e: Exception) {
                    false
                }
                if (copied) {
                    slides.add(dst)
                    ok++
                }
            }
            countText.text = "ရွေးထားတဲ့ ပုံ: " + slides.size + " ပုံ"
            Toast.makeText(this, "ပုံ " + ok + " ပုံ ထည့်ပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else if (requestCode == 91 && data.data != null) {
            val uri = data.data!!
            val work = File(cacheDir, "slides")
            work.mkdirs()
            val dst = File(work, "slide_bgm." + extOf(uri))
            val copied = try {
                dst.delete()
                contentResolver.openInputStream(uri)?.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                dst.length() > 0L
            } catch (e: Exception) {
                false
            }
            if (copied) {
                audioFile = dst
                audioNameText.text = "BGM audio ရွေးပြီးပါပြီ ✓ - " + dst.name
                Toast.makeText(this, "Audio ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Audio ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun normalize(src: File, dst: File) {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val bm = BitmapFactory.decodeFile(src.absolutePath, opts) ?: throw IOException("ပုံဖတ်မရပါ: " + src.name)
        FileOutputStream(dst).use { o -> bm.compress(Bitmap.CompressFormat.JPEG, 88, o) }
        bm.recycle()
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
        if (slides.isEmpty()) {
            Toast.makeText(this, "အရင်ဆုံ ဓာတ်ပုံ ရွေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        progressBar.visibility = View.VISIBLE
        statusText.text = "ပုံတွေ ပြင်နေသည်..."
        lifecycleScope.launch {
            try {
                val work = File(cacheDir, "slides_out")
                work.mkdirs()
                val normFx: List<Effect> = ImmutableList.copyOf(listOf<Effect>(androidx.media3.effect.Presentation.createForHeight(720)))
                val items = ArrayList<EditedMediaItem>()
                withContext(Dispatchers.IO) {
                    for (i in slides.indices) {
                        statusText.text = "ပုံ " + (i + 1) + "/" + slides.size + " ပြင်နေသည်..."
                        val jpg = File(work, "s_" + i + ".jpg")
                        normalize(slides[i], jpg)
                        val mi = MediaItem.Builder().setUri(Uri.fromFile(jpg)).setMimeType(MimeTypes.IMAGE_JPEG).build()
                        items.add(EditedMediaItem.Builder(mi).setDurationUs(secPer * 1000000L).setEffects(Effects(ImmutableList.of(), ImmutableList.copyOf(normFx))).build())
                    }
                }
                val seqs = ArrayList<EditedMediaItemSequence>()
                seqs.add(EditedMediaItemSequence(ImmutableList.copyOf(items)))
                val audio = audioFile
                if (audio != null && audio.exists()) {
                    val totalMs = slides.size.toLong() * secPer * 1000L
                    var bgmMs = 0L
                    runCatching {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(audio.absolutePath)
                        bgmMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        mmr.release()
                    }
                    if (bgmMs <= 0L) bgmMs = 60000L
                    val vol = bgmVol
                    val bgmItems = ArrayList<EditedMediaItem>()
                    var covered = 0L
                    while (covered < totalMs && bgmItems.size < 80) {
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
                        val mi = MediaItem.Builder().setUri(Uri.fromFile(audio)).build()
                        val aps: List<androidx.media3.common.audio.AudioProcessor> = ImmutableList.of(mix)
                        bgmItems.add(EditedMediaItem.Builder(mi).setEffects(Effects(aps, ImmutableList.of())).build())
                        covered += bgmMs
                    }
                    if (bgmItems.isNotEmpty()) seqs.add(EditedMediaItemSequence(ImmutableList.copyOf(bgmItems)))
                }
                statusText.text = "Slideshow ဖွဲ့စည်းနေသည်..."
                val out = File(cacheDir, "slide_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seqs).build(), out) }
                val name = "SLIDE_" + System.currentTimeMillis() + ".mp4"
                val ok = DownloadsUtil.saveFile(this@SlideshowActivity, out, name, "video/mp4")
                busy = false
                progressBar.visibility = View.GONE
                if (ok) {
                    statusText.text = "ပြီးပါပြီ - Downloads မှာ " + name
                    Toast.makeText(this, "ပြီးပါပြီ - Downloads မှာ " + name, Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "သိမ်းရာမှာ မအောင်မြင်ပါ"
                    Toast.makeText(this, "သိမ်းရာမှာ မအောင်မြင်ပါ", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                busy = false
                progressBar.visibility = View.GONE
                statusText.text = "မအောင်မြင်: " + (e.message ?: "")
                Toast.makeText(this, "မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}
