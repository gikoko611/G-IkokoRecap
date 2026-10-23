package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
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
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.gikoko.recap.recap.DownloadsUtil
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SubtitleBurnActivity : AppCompatActivity() {

    private data class Cue(val startMs: Long, val endMs: Long, val text: String)

    private var srcFile: File? = null
    private var srtFile: File? = null
    private var busy = false

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var videoNameText: TextView
    private lateinit var srtNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open subtitle burn")
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
        ht.text = "SUBTITLE BURN"
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
        statusText.text = "Video ရယ် .srt file ရယ် ရွေးပြီး - video ပေါ်ကို စာလုံးများ တိုက်ရိုက်တင်ပေးမယ်"
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

        val cst = card()
        cst.addView(sectionTitle("💬 SUBTITLE (.srt)"))
        val pickS = flatBtn("📁 SRT FILE ရွေးမည်", R.drawable.bg_chip_selected, "#F2F5FF")
        cst.addView(pickS)
        pickS.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.setType("*/*")
            startActivityForResult(i, 91)
        }
        srtNameText = note("SRT မရွေးရသေးပါ")
        srtNameText.setPadding(0, dp(8), 0, 0)
        cst.addView(srtNameText)
        val ns = note("HISTORY ထဲက recap တွေမှာ SRT EXPORT နဲ့ထုတ်ထားတဲ့ recap.srt ရွေးလို့ရပါတယ်။ အများဆုံး စာပိုဒ် ၆၀ ထိ လက်ခံပါတယ်။")
        ns.setPadding(0, dp(8), 0, 0)
        cst.addView(ns)
        root.addView(cst)

        val cc = card()
        val runBtn = flatBtn("💬 စာတင်ပြီး ထုတ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        cc.addView(runBtn)
        runBtn.setOnClickListener { run() }
        root.addView(cc)

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
        if (resultCode != -1 || data?.data == null) return
        val uri = data.data!!
        val work = File(cacheDir, "subburn")
        work.mkdirs()
        if (requestCode == 90) {
            val dst = File(work, "sb_src." + extOf(uri))
            if (copyUri(uri, dst)) {
                srcFile = dst
                videoNameText.text = dst.name + "  -  " + (mediaDurationMs(dst) / 1000L) + " sec"
                Toast.makeText(this, "Video ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Video ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 91) {
            val dst = File(work, "sb.srt")
            if (copyUri(uri, dst)) {
                srtFile = dst
                srtNameText.text = "SRT ရွေးပြီးပါပြီ ✓ - " + dst.name
                Toast.makeText(this, "SRT ရွေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SRT ကူးယူမအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
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

    private fun parseTs(v: String): Long? {
        val t = v.trim().replace(',', '.')
        val p = t.split(':')
        if (p.size < 3) return null
        val h = p[0].toLongOrNull() ?: return null
        val m = p[1].toLongOrNull() ?: return null
        val sec = p[2].toFloatOrNull() ?: return null
        return h * 3600000L + m * 60000L + (sec * 1000L).toLong()
    }

    private fun parseSrt(txt: String): ArrayList<Cue> {
        val out = ArrayList<Cue>()
        val blocks = txt.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
        for (b in blocks) {
            val lines = b.trim().split('\n').filter { l -> l.trim().isNotEmpty() }
            if (lines.isEmpty()) continue
            val tsIdx = lines.indexOfFirst { l -> l.contains("-->") }
            if (tsIdx < 0 || tsIdx + 1 >= lines.size) continue
            val parts = lines[tsIdx].split("-->")
            if (parts.size < 2) continue
            val s = parseTs(parts[0]) ?: continue
            val e = parseTs(parts[1]) ?: continue
            val text = lines.subList(tsIdx + 1, lines.size).joinToString("\n").trim()
            if (text.isNotEmpty() && e > s) out.add(Cue(s, e, text))
        }
        return out
    }

    private fun clipItem(video: File, startMs: Long, endMs: Long, text: String?): EditedMediaItem {
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()
        val mi = MediaItem.Builder().setUri(Uri.fromFile(video)).setClippingConfiguration(clip).build()
        val b = EditedMediaItem.Builder(mi)
        if (text != null) {
            val overlay = TextOverlay.createStaticTextOverlay(SpannableString(text))
            val fx: List<Effect> = ImmutableList.of<Effect>(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay)))
            b.setEffects(Effects(ImmutableList.of(), fx))
        }
        return b.build()
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
        val srt = srtFile
        if (video == null || !video.exists()) {
            Toast.makeText(this, "အရင်ဆုံ VIDEO ရွေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        if (srt == null || !srt.exists()) {
            Toast.makeText(this, "အရင်ဆုံ SRT FILE ရွေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        progressBar.visibility = View.VISIBLE
        statusText.text = "SRT ဖတ်နေသည်..."
        lifecycleScope.launch {
            try {
                val cues = withContext(Dispatchers.IO) { parseSrt(srt.readText()) }
                if (cues.isEmpty()) throw IOException("SRT ထဲ စာပိုဒ် မတွေ့ပါ")
                if (cues.size > 60) throw IOException("စာပိုဒ် " + cues.size + " များနေပါတယ် - အများဆုံး ၆၀ ထိ")
                val durMs = mediaDurationMs(video)
                if (durMs <= 0L) throw IOException("Video duration မရနိုင်ပါ")
                cues.sortBy { c -> c.startMs }
                val items = ArrayList<EditedMediaItem>()
                var cur = 0L
                for (cue in cues) {
                    val s = maxOf(cue.startMs, cur)
                    val e = minOf(cue.endMs, durMs)
                    if (e <= s) continue
                    if (s > cur + 100L) items.add(clipItem(video, cur, s, null))
                    items.add(clipItem(video, s, e, cue.text))
                    cur = e
                }
                if (cur < durMs - 100L) items.add(clipItem(video, cur, durMs, null))
                if (items.isEmpty()) throw IOException("ထည့်စရာ စာပိုဒ် မရှိပါ")
                statusText.text = "Video ပေါ် စာတင်နေသည်... (" + items.size + " ပိုဒ်)"
                val seq = EditedMediaItemSequence(ImmutableList.copyOf(items))
                val out = File(cacheDir, "subburn_out.mp4")
                withContext(Dispatchers.Main) { export(Composition.Builder(seq).build(), out) }
                val name = "SUB_" + System.currentTimeMillis() + ".mp4"
                val ok = DownloadsUtil.saveFile(this@SubtitleBurnActivity, out, name, "video/mp4")
                busy = false
                progressBar.visibility = View.GONE
                if (ok) {
                    statusText.text = "ပြီးပါပြီ - Downloads မှာ " + name
                    Toast.makeText(this@SubtitleBurnActivity, "ပြီးပါပြီ - Downloads မှာ " + name, Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "သိမ်းရာမှာ မအောင်မြင်ပါ"
                }
            } catch (e: Exception) {
                busy = false
                progressBar.visibility = View.GONE
                statusText.text = "မအောင်မြင်: " + (e.message ?: "")
                Toast.makeText(this@SubtitleBurnActivity, "မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}
