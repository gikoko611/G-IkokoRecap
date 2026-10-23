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
import android.text.SpannableString
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.gikoko.recap.recap.DownsloadsUtil
import com.gikoko.recap.recap.FrameRenderer
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

class VideoLabActivity : AppCompatActivity() {

    private var srcFile: File? = null
    private var audioFile: File? = null
    private var bgmVol = 0.4f
    private var busy = false

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var videoNameText: TextView
    private lateinit var audioNameText: TextView
    private lateinit var trimNote: TextView
    private lateinit var trimStartInput: EditText
    private lateinit var trimEndInput: EditText
    private lateinit var overlayInput: EditText
    private lateinit var introTitleInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open video lab")
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

    private fun inputStyle(e: EditText) {
        e.setBackgroundResource(R.drawable.bg_input)
        e.setTextColor(Color.parseColor("#F2F5FF"))
        e.setHintTextColor(Color.parseColor("#5A6480"))
        e.textSize = 14f
        e.setSingleLine(true)
        e.setPadding(dp(12), dp(12), dp(12), dp(12))
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

    private fun btnParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(dp(4), 0, dp(4), 0)
        return p
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
        ht.text = "VIDEO LAB"
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
        statusText.text = "Video တစ်ခု ရွေးပြီး အောက်က tool တစ်ခုနဲ့ ဆက်လုပ်ပါ"
        cs.addView(statusText)
        root.addView(cs)

        val cv = card()
        cv.addView(sectionTitle("📹 SOURCE VIDEO"))
        val pickV = flatBtn("📁 VIDEO ရွေးမည်", R.drawable.bg_gold_btn, "#0A0A00")
        cv.addView(pickV)
        pickV.setOnClickListener { pickVideo() }
        videoNameText = note("Video မရွေးရသေးပါ - အရင် video ရွေးပါ")
        videoNameText.setPadding(0, dp(8), 0, 0)
        cv.addView(videoNameText)
        root.addView(cv)

        val cb = card()
        cb.addView(sectionTitle("🎵 BGM ADDER - သီချင်း ထည့်မည်"))
        val pickA = flatBtn("🎵 BGM AUDIO ရွေးမည်", R.drawable.bg_chip_selected, "#F2F5FF")
        cb.addView(pickA)
        pickA.setOnClickListener { pickAudio() }
        audioNameText = note("Audio မရွေးရသေးပါ")
        audioNameText.setPadding(0, dp(8), 0, 0)
        cb.addView(audioNameText)
        cb.addView(sectionTitle("BGM VOLUME"))
        val vg = ChipGroup(this)
        vg.isSingleSelection = true
        vg.isSelectionRequired = true
        val volVals = floatArrayOf(0.2f, 0.4f, 0.6f)
        listOf("20%", "40%", "60%").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 63000 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) bgmVol = volVals[i] }
            vg.addView(ch)
        }
        vg.check(63001)
        cb.addView(vg, topParams())
        val runB = flatBtn("🎵 BGM ထည့်ပြီး ထုတ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        cb.addView(runB, topParams())
        runB.setOnClickListener { runBgm() }
        val nb = note("Video အသံကို ထိန်းထားပြီး အပေါ်မှာ BGM ထပ်တင်ပါမယ် - Downloads မှာ BGM_ နာမည်နဲ့ သိမ်းပါမယ်။")
        nb.setPadding(0, dp(8), 0, 0)
        cb.addView(nb)
        root.addView(cb)

        val ct = card()
        ct.addView(sectionTitle("✂️ TRIM - ခုံဖြတ်မည်"))
        val rowT = LinearLayout(this)
        rowT.orientation = LinearLayout.HORIZONTAL
        ct.addView(rowT, topParams())
        trimStartInput = EditText(this)
        trimStartInput.hint = "စ (second)"
        inputStyle(trimStartInput)
        trimStartInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        rowT.addView(trimStartInput, btnParams())
        trimEndInput = EditText(this)
        trimEndInput.hint = "ဆုံး (second)"
        inputStyle(trimEndInput)
        trimEndInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        rowT.addView(trimEndInput, btnParams())
        trimNote = note("Video ရွေးရင် ကြာချိန် ပြပါမယ်")
        trimNote.setPadding(0, dp(8), 0, 0)
        ct.addView(trimNote)
        val runT = flatBtn("✂️ ဖြတ်ပြီး ထုတ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        ct.addView(runT, topParams())
        runT.setOnClickListener { runTrim() }
        root.addView(ct)

        val co = card()
        co.addView(sectionTitle("📝 TEXT OVERLAY - စာထည့်မည်"))
        overlayInput = EditText(this)
        overlayInput.hint = "Video ပေါ်မှာ ပေါ်စေချင်တဲ့ စာသား"
        inputStyle(overlayInput)
        overlayInput.setSingleLine(false)
        overlayInput.minLines = 2
        co.addView(overlayInput)
        val runO = flatBtn("📝 စာထည့်ပြီး ထုတ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        co.addView(runO, topParams())
        runO.setOnClickListener { runOverlay() }
        val no = note("စာသားက video ရဲ့ အလယ်မှာ ပေါ်မယ် - အများဆုံး စာလုံး 120။")
        no.setPadding(0, dp(8), 0, 0)
        co.addView(no)
        root.addView(co)

        val ci = card()
        ci.addView(sectionTitle("🎬 INTRO CARD - စတင်ချက်"))
        introTitleInput = EditText(this)
        introTitleInput.hint = "Intro card ပေါ်မှာ ပေါ်စေချင်တဲ့ ခေါင်းစဉ် (ချန်လို့ရ)"
        inputStyle(introTitleInput)
        ci.addView(introTitleInput)
        val runI = flatBtn("🎬 INTRO တပ်ပြီး ထုတ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        ci.addView(runI, topParams())
        runI.setOnClickListener { runIntro() }
        val ni = note("GIkoko branded intro card (2.5 sec) ကို video ရှေ့မှာ တပ်ပါမယ် - theme/watermark က TOOLS ထဲက ရွေးထားတဲ့အတိုင်း။")
        ni.setPadding(0, dp(8), 0, 0)
        ci.addView(ni)
        root.addView(ci)

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

    private fun pickAudio() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.setType("audio/*")
        startActivityForResult(i, 91)
    }

    private fun extOf(uri: Uri): String {
        return try {
            val n = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val dot = n?.lastIndexOf('.')
            if (n != null && dot != null && dot < n.length - 1) n.substring(dot + 1).lowercase().take(5) else "mp3"
        } catch (e: Exception) {
            "mp3"
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
        if (resultCode != -1 || data?.data == null) return
        val uri = data.data!!
        val work = File(cacheDir, "videolab")
        work.mkdirs()
        if (requestCode == 90) {
            val dst = File(work, "vl_src." + extOf(uri))
            if (copyUri(uri, dst)) {
                srcFile = dst
                val dur = mediaDurationMs(dst) / 1000L
                videoNameText.text = dst.name + "  -  " + dur + " sec"
                trimNote.text = "Video ကြာချိန် " + dur + " စက္ကန့် - 0 မှ " + dur + " ကြားထဲ start/end ထည့်ပါ"
                Toast.makeText(this, "Video ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Video ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 91) {
            val dst = File(work, "vl_bgm." + extOf(uri))
            if (copyUri(uri, dst)) {
                audioFile = dst
                audioNameText.text = "BGM audio ရွေးပြီးပါပြီ ✓ - " + dst.name
                Toast.makeText(this, "Audio ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Audio ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
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

    private fun guard(): Boolean {
        if (busy) {
            Toast.makeText(this, "တစ်ခု လုပ်နေသေးသည် - ပြီးရင် နောက်တစ်ခု လုပ်ပါ", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun needVideo(): File? {
        val f = srcFile
        if (f == null || !f.exists()) {
            Toast.makeText(this, "အရင်ဆုံ VIDEO ရွေးပါ", Toast.LENGTH_SHORT).show()
        }
        return f
    }

    private fun startJob(label: String) {
        busy = true
        statusText.text = label
        progressBar.visibility = View.VISIBLE
    }

    private fun endJob(ok: Boolean, msg: String) {
        busy = false
        progressBar.visibility = View.GONE
        statusText.text = msg
        Toast.makeText(this, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }

    private fun finishSave(out: File, prefix: String) {
        val name = prefix + "_" + System.currentTimeMillis() + ".mp4"
        val ok = DownloadsUtil.saveFile(this, out, name, "video/mp4")
        if (ok) endJob(true, "ပြီးပါပြီ - Downloads မှာ " + name)
        else endJob(false, "သိမ်းရာမှာ မအောင်မြင်ပါ")
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

    private fun runBgm() {
        if (!guard()) return
        val video = needVideo() ?: return
        val audio = audioFile
        if (audio == null || !audio.exists()) {
            Toast.makeText(this, "အရင်ဆုံ BGM AUDIO ရွေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        startJob("BGM ရောစပ်နေသည်...")
        lifecycleScope.launch {
            try {
                val totalMs = mediaDurationMs(video)
                if (totalMs <= 0L) throw IOException("Video duration မရနိုင်ပါ")
                val vol = bgmVol
                val bgmItems = ArrayList<EditedMediaItem>()
                var bgmMs = mediaDurationMs(audio)
                if (bgmMs <= 0L) bgmMs = 60000L
                var covered = 0L
                while (covered < totalMs && bgmItems.size < 60) {
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
                val videoItem = EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(video)).build()).build()
                val seqs = ArrayList<EditedMediaItemSequence>()
                seqs.add(EditedMediaItemSequence(ImmutableList.of(videoItem)))
                seqs.add(EditedMediaItemSequence(ImmutableList.copyOf(bgmItems)))
                val out = File(cacheDir, "videolab_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seqs).build(), out) }
                finishSave(out, "BGM")
            } catch (e: Exception) {
                endJob(false, "မအောင်မြင်: " + (e.message ?: ""))
            }
        }
    }

    private fun runTrim() {
        if (!guard()) return
        val video = needVideo() ?: return
        val start = trimStartInput.text.toString().trim().toLongOrNull()
        val end = trimEndInput.text.toString().trim().toLongOrNull()
        if (start == null || end == null) {
            Toast.makeText(this, "start/end စက္ကန့် ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val durSec = mediaDurationMs(video) / 1000L
        if (durSec <= 0L) {
            Toast.makeText(this, "Video duration မရနိုင်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        if (start < 0L || end <= start || end > durSec) {
            Toast.makeText(this, "0 မှ " + durSec + " ကြားမှာပဲ - start < end ဖြစ်ရမယ်", Toast.LENGTH_LONG).show()
            return
        }
        startJob("ခုံဖြတ်နေသည်...")
        lifecycleScope.launch {
            try {
                val clipping = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(start * 1000L)
                    .setEndPositionMs(end * 1000L)
                    .build()
                val mi = MediaItem.Builder().setUri(Uri.fromFile(video)).setClippingConfiguration(clipping).build()
                val item = EditedMediaItem.Builder(mi).build()
                val seq = EditedMediaItemSequence(ImmutableList.of(item))
                val out = File(cacheDir, "videolab_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seq).build(), out) }
                finishSave(out, "TRIM")
            } catch (e: Exception) {
                endJob(false, "မအောင်မြင်: " + (e.message ?: ""))
            }
        }
    }

    private fun runOverlay() {
        if (!guard()) return
        val video = needVideo() ?: return
        var txt = overlayInput.text.toString().trim()
        if (txt.isEmpty()) {
            Toast.makeText(this, "စာသား ရိုက်ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        if (txt.length > 120) txt = txt.substring(0, 120)
        startJob("စာထည့်နေသည်...")
        lifecycleScope.launch {
            try {
                val overlay = TextOverlay.createStaticTextOverlay(SpannableString(txt))
                val texList: ImmutableList<TextureOverlay> = ImmutableList.of(overlay)
                val fx: List<Effect> = ImmutableList.of(OverlayEffect(texList))
                val mi = MediaItem.Builder().setUri(Uri.fromFile(video)).build()
                val item = EditedMediaItem.Builder(mi).setEffects(Effects(ImmutableList.of(), fx)).build()
                val seq = EditedMediaItemSequence(ImmutableList.of(item))
                val out = File(cacheDir, "videolab_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seq).build(), out) }
                finishSave(out, "TEXT")
            } catch (e: Exception) {
                endJob(false, "မအောင်မြင်: " + (e.message ?: ""))
            }
        }
    }

    private fun runIntro() {
        if (!guard()) return
        val video = needVideo() ?: return
        val title = introTitleInput.text.toString().trim()
        startJob("Intro card ဖန်တီးနေသည်...")
        lifecycleScope.launch {
            try {
                val work = File(cacheDir, "videolab")
                work.mkdirs()
                val introF = File(work, "vl_intro.jpg")
                withContext(Dispatchers.IO) {
                    FrameRenderer.renderBranded(introF, true, Prefs.watermark(this@VideoLabActivity), false, Prefs.wmSize(this@VideoLabActivity), Prefs.wmPos(this@VideoLabActivity), Prefs.wmLogo(this@VideoLabActivity), Prefs.theme(this@VideoLabActivity))
                    if (title.isNotEmpty()) {
                        val bm = BitmapFactory.decodeFile(introF.absolutePath)
                        if (bm != null) {
                            val c = Canvas(bm)
                            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
                            tp.color = Color.WHITE
                            tp.textSize = 44f
                            tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            tp.textAlign = Paint.Align.CENTER
                            val disp = if (title.length > 34) title.substring(0, 34) + "..." else title
                            c.drawText(disp, (bm.width / 2).toFloat(), (bm.height / 2 + 330).toFloat(), tp)
                            FileOutputStream(introF).use { o -> bm.compress(Bitmap.CompressFormat.JPEG, 90, o) }
                            bm.recycle()
                        }
                    }
                }
                val normFx: List<Effect> = ImmutableList.copyOf(listOf<Effect>(Presentation.createForHeight(720)))
                val introMi = MediaItem.Builder().setUri(Uri.fromFile(introF)).setMimeType(MimeTypes.IMAGE_JPEG).build()
                val introItem = EditedMediaItem.Builder(introMi).setDurationUs(2500000L).setEffects(Effects(ImmutableList.of(), ImmutableList.copyOf(normFx))).build()
                val videoMi = MediaItem.Builder().setUri(Uri.fromFile(video)).build()
                val videoItem = EditedMediaItem.Builder(videoMi).setEffects(Effects(ImmutableList.of(), ImmutableList.copyOf(normFx))).build()
                val seq = EditedMediaItemSequence(ImmutableList.of(introItem, videoItem))
                val out = File(cacheDir, "videolab_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seq).build(), out) }
                finishSave(out, "INTRO")
            } catch (e: Exception) {
                endJob(false, "မအောင်မြင်: " + (e.message ?: ""))
            }
        }
    }
}
