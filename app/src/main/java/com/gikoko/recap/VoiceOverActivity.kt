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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.gikoko.recap.net.Tts
import com.gikoko.recap.recap.DownsloadsUtil
import com.gikoko.recap.recap.WavUtil
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

class VoiceOverActivity : AppCompatActivity() {

    private var srcFile: File? = null
    private var speed = 1.0
    private var busy = false

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var videoNameText: TextView
    private lateinit var textInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open voice over")
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
        ht.text = "AI VOICE-OVER"
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
        statusText.text = "Video ရွေးပြီး ဖတ်စေချင်တဲ့ စာသား ရိုက်ပါ - မူလအသံနေရာမှာ AI အသံနဲ့ အစားထိုးပါမယ်"
        cs.addView(statusText)
        root.addView(cs)

        val cv = card()
        cv.addView(sectionTitle("📹 SOURCE VIDEO"))
        val pickV = flatBtn("📁 VIDEO ရွေးမည်", R.drawable.bg_gold_btn, "#0A0A00")
        cv.addView(pickV)
        pickV.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.setType("video/*")
            startActivityForResult(i, 90)
        }
        videoNameText = note("Video မရွေးရသေးပါ")
        videoNameText.setPadding(0, dp(8), 0, 0)
        cv.addView(videoNameText)
        root.addView(cv)

        val ct = card()
        ct.addView(sectionTitle("🗣 ဖတ်စေချင်တဲ့ စာသား"))
        textInput = EditText(this)
        textInput.hint = "Video ပေါ်မှာ ဖတ်ပေးစေချင်တဲ့ စာသား ရိုက်ထည့်ပါ"
        textInput.setBackgroundResource(R.drawable.bg_input)
        textInput.setTextColor(Color.parseColor("#F2F5FF"))
        textInput.setHintTextColor(Color.parseColor("#5A6480"))
        textInput.textSize = 13f
        textInput.setSingleLine(false)
        textInput.minLines = 5
        textInput.gravity = Gravity.TOP
        textInput.setPadding(dp(12), dp(12), dp(12), dp(12))
        ct.addView(textInput)
        ct.addView(sectionTitle("SPEED"))
        val sg = ChipGroup(this)
        sg.isSingleSelection = true
        sg.isSelectionRequired = true
        val speeds = doubleArrayOf(0.9, 1.0, 1.15)
        listOf("0.9x", "1.0x", "1.15x").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 65000 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) speed = speeds[i] }
            sg.addView(ch)
        }
        sg.check(65001)
        ct.addView(sg, topParams())
        val runBtn = flatBtn("🗣 VOICE-OVER ထုတ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        ct.addView(runBtn, topParams())
        runBtn.setOnClickListener { run() }
        val n1 = note("VOICE LAB မှာ ရွေးထားတဲ့ အသံ + Edge TTS (key မလို) ကို သုံးပါမယ် - ရလဒ်က VO_ နာမည်နဲ့ Downloads မှာ သိမ်းပါမယ်။ စာသားရှည်လွန်းရင် video ကြာချိန် မလုံ့လို့ ငြင်းပါမယ်။")
        n1.setPadding(0, dp(8), 0, 0)
        ct.addView(n1)
        root.addView(ct)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 90 || resultCode != -1 || data?.data == null) return
        val uri = data.data!!
        val work = File(cacheDir, "voiceover")
        work.mkdirs()
        val dst = File(work, "vo_src.mp4")
        val ok = try {
            dst.delete()
            contentResolver.openInputStream(uri)?.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
            dst.length() > 0L
        } catch (e: Exception) {
            false
        }
        if (ok) {
            srcFile = dst
            val dur = mediaDurationMs(dst) / 1000L
            videoNameText.text = dst.name + "  -  " + dur + " sec"
            Toast.makeText(this, "Video ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Video ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
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

    private fun splitText(txt: String): List<String> {
        val parts = ArrayList<String>()
        var cur = StringBuilder()
        val delims = charArrayOf('.', '!', '?', '\u1002', '\n', ';')
        for (ch in txt) {
            cur.append(ch)
            val flush = delims.contains(ch) && cur.length > 40
            if (cur.length >= 280 || flush) {
                val s = cur.toString().trim()
                if (s.isNotEmpty()) parts.add(s)
                cur = StringBuilder()
            }
        }
        val rest = cur.toString().trim()
        if (rest.isNotEmpty()) parts.add(rest)
        return parts.filter { it.isNotEmpty() }
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
        if (txt.length < 2) {
            Toast.makeText(this, "စာသား ရိုက်ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        startJob("အသံထုတ်နေသည် (TTS)...")
        lifecycleScope.launch {
            try {
                val apiKey = Prefs.apiKey(this@VoiceOverActivity)
                val chunks = splitText(txt)
                statusText.text = "TTS " + chunks.size + " ပိုင်း - ထုတ်နေသည်..."
                val pcmParts = ArrayList<ByteArray>()
                var rate = 24000
                for (c in chunks) {
                    val (pcm, r) = withContext(Dispatchers.IO) {
                        Tts.generate(this@VoiceOverActivity, apiKey, c, Prefs.voice(this@VoiceOverActivity), speed, Tts.defaultEdgeVoice(this@VoiceOverActivity))
                    }
                    rate = r
                    pcmParts.add(pcm)
                }
                Prefs.bumpUsage(this@VoiceOverActivity, Prefs.KEY_U_TTS, pcmParts.size)
                var total = 0
                for (p in pcmParts) total += p.size
                val pcm = ByteArray(total)
                var off = 0
                for (p in pcmParts) {
                    System.arraycopy(p, 0, pcm, off, p.size)
                    off += p.size
                }
                val work = File(cacheDir, "voiceover")
                work.mkdirs()
                val wavFile = File(work, "vo_tts.wav")
                withContext(Dispatchers.IO) { wavFile.writeBytes(WavUtil.pcmToWav(pcm, rate)) }
                val ttsMs = WavUtil.wavDurationMs(wavFile.readBytes())
                val videoMs = mediaDurationMs(video)
                if (ttsMs <= 0L) throw IOException("TTS အသံ မရနိုင်ပါ")
                if (videoMs > 0L && ttsMs > videoMs) {
                    throw IOException("စာသားရှည်လွန်းပါတယ် - TTS " + (ttsMs / 1000L) + "s / video " + (videoMs / 1000L) + "s - စာသားကို တိုပါ")
                }
                val videoItem = EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(video)).build()).setRemoveAudio(true).build()
                val audioItem = EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(wavFile)).build()).build()
                val seqs = ArrayList<EditedMediaItemSequence>()
                seqs.add(EditedMediaItemSequence(ImmutableList.of(videoItem)))
                seqs.add(EditedMediaItemSequence(ImmutableList.of(audioItem)))
                val out = File(cacheDir, "vo_out.mp4")
                statusText.text = "Video ဖွဲ့စည်းနေသည်..."
                withContext(Dispatchers.Main) { export(Composition.Builder(seqs).build(), out) }
                val name = "VO_" + System.currentTimeMillis() + ".mp4"
                val okSave = DownloadsUtil.saveFile(this@VoiceOverActivity, out, name, "video/mp4")
                if (okSave) endJob(true, "ပြီးပါပြီ - Downloads မှာ " + name) else endJob(false, "သိမ်းရာမှာ မအောင်မြင်ပါ")
            } catch (e: Exception) {
                endJob(false, "မအောင်မြင်: " + (e.message ?: ""))
            }
        }
    }
}
