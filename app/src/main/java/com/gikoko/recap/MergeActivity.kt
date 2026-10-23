package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.google.common.collect.ImmutableList
import com.gikoko.recap.recap.DownloadsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MergeActivity : AppCompatActivity() {

    private val files = ArrayList<File>()
    private val names = ArrayList<String>()
    private val durs = ArrayList<Long>()
    private lateinit var listBox: LinearLayout
    private lateinit var countText: TextView
    private lateinit var mergeBtn: Button
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private var merging = false

    private var mergeDone: ((ExportException?) -> Unit)? = null

    private val pickVideos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onVideosPicked(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open merger")
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
        ht.text = "VIDEO MERGER"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c1 = card()
        c1.addView(sectionTitle("🎞 VIDEO တွေ ပေါင်းမည်"))
        val addBtn = Button(this)
        addBtn.text = "➕ VIDEO ထပ်ထည့်မည် (MULTI PICK)"
        addBtn.isAllCaps = false
        addBtn.textSize = 13f
        addBtn.setTextColor(Color.parseColor("#0A0A00"))
        addBtn.setTypeface(null, Typeface.BOLD)
        addBtn.setBackgroundResource(R.drawable.bg_gold_btn)
        addBtn.setPadding(dp(8), dp(14), dp(8), dp(14))
        addBtn.setOnClickListener { if (!merging) pickVideos.launch(arrayOf("video/*")) }
        c1.addView(addBtn)
        countText = TextView(this)
        countText.setTextColor(Color.parseColor("#8A93B2"))
        countText.textSize = 12f
        countText.setPadding(dp(4), dp(8), dp(4), 0)
        c1.addView(countText)
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        c1.addView(listBox)
        val note1 = note("Video အတိုလေးတွေ (ဥပမာ ၅ စက္ကန့်) တွေကို တန်းစီပြီး တစ်ခုတည်းအဖြစ် ပေါင်းပေးမယ် - 5 ဖိုင် × ၅ စက္ကန့်ဆို ၂၅ စက္ကန့် video ရမယ်၊ 60 ဖိုင်ဆို ၅ မိနစ် ရပါမယ်။ ⬆ ⬇ နှိပ်ပြီး အစဉ်လိုက် ပြောင်းလို့ရပါတယ်။")
        note1.setPadding(0, dp(8), 0, 0)
        c1.addView(note1)
        root.addView(c1)

        mergeBtn = Button(this)
        mergeBtn.text = "▶ MERGE - ပေါင်းမည်"
        mergeBtn.isAllCaps = false
        mergeBtn.textSize = 16f
        mergeBtn.setTypeface(null, Typeface.BOLD)
        mergeBtn.setTextColor(Color.parseColor("#0A0A00"))
        mergeBtn.setBackgroundResource(R.drawable.bg_start_btn)
        mergeBtn.letterSpacing = 0.1f
        mergeBtn.setPadding(0, dp(16), 0, dp(16))
        val sp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sp.setMargins(dp(16), dp(16), dp(16), dp(8))
        root.addView(mergeBtn, sp)
        mergeBtn.setOnClickListener { startMerge() }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.isIndeterminate = true
        progress.visibility = View.GONE
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pp.setMargins(dp(16), 0, dp(16), 0)
        root.addView(progress, pp)

        statusText = TextView(this)
        statusText.setTextColor(Color.parseColor("#3DE8F8"))
        statusText.textSize = 12f
        statusText.setPadding(dp(16), dp(6), dp(16), 0)
        root.addView(statusText)

        val clearBtn = Button(this)
        clearBtn.text = "🗑 LIST အားလုံး ဖျက်မည်"
        clearBtn.isAllCaps = false
        clearBtn.textSize = 13f
        clearBtn.setTextColor(Color.parseColor("#F2F5FF"))
        clearBtn.setBackgroundResource(R.drawable.bg_chip_selected)
        clearBtn.setPadding(0, dp(12), 0, dp(12))
        val cp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cp.setMargins(dp(16), dp(10), dp(16), dp(8))
        root.addView(clearBtn, cp)
        clearBtn.setOnClickListener { if (!merging) clearList() }

        val warn = note("⚠️ ပေါင်းနေစဉ်မှာ ဖုန်း screen မပိတ်ပါနဲ့ (app က screen ဖွင့်ထားပေးပါမယ်)။ Video ရှည်ရင် ခဏခန့် ကြာနိုင်ပါတယ် - ဖိုင်အရွယ် အကြမ်း ၅ မိနစ်ခန့်ဆို ၁-၃ မိနစ်ဝန်းကျင် ကြာပါမယ်။ ပြီးရင် Downloads ထဲမှာ MERGED_xxx.mp4 နာမည်နဲ့ သိမ်းပေးပါမယ်။ အသံပါတဲ့ video တွေနဲ့ပဲ ပေါင်းသင့်ပါတယ်။")
        warn.setPadding(dp(16), dp(8), dp(16), dp(16))
        root.addView(warn)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshList()
    }

    private fun queryName(uri: Uri): String {
        var name = "video.mp4"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        } catch (e: Exception) {
            // keep default
        }
        return name
    }

    private fun durationMs(f: File): Long {
        return try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(f.absolutePath)
            val d = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
            d
        } catch (e: Exception) {
            0L
        }
    }

    private fun fmtMs(ms: Long): String {
        val s = ms / 1000L
        if (s >= 3600L) return String.format("%dh %02dm %02ds", s / 3600L, (s % 3600L) / 60L, s % 60L)
        if (s >= 60L) return String.format("%dm %02ds", s / 60L, s % 60L)
        return s.toString() + "s"
    }

    private fun onVideosPicked(uris: List<Uri>) {
        Toast.makeText(this, uris.size.toString() + " ဖိုင် ကူးယူနေသည်...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val dir = File(filesDir, "merge")
                dir.mkdirs()
                val added = withContext(Dispatchers.IO) {
                    var n = 0
                    uris.forEachIndexed { i, uri ->
                        val disp = queryName(uri)
                        val ext = if (disp.lastIndexOf('.') > 0) disp.substring(disp.lastIndexOf('.')) else ".mp4"
                        val dst = File(dir, "m_" + System.currentTimeMillis() + "_" + i + ext)
                        contentResolver.openInputStream(uri)?.use { ins ->
                            dst.outputStream().use { ins.copyTo(it) }
                        }
                        if (dst.length() > 0L) {
                            files.add(dst)
                            names.add(disp)
                            durs.add(durationMs(dst))
                            n++
                        }
                    }
                    n
                }
                runOnUiThread {
                    refreshList()
                    Toast.makeText(this@MergeActivity, "ထည့်ပြီးပါပြီ ✓ (" + added + " ဖိုင်)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MergeActivity, "ကူးယူမအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshList() {
        listBox.removeAllViews()
        var total = 0L
        durs.forEach { total += it }
        countText.text = if (files.isEmpty()) "List ဗလာ - VIDEO ထည့်ပြီး MERGE နှိပ်ပါ" else ("Video " + files.size + " ခု - ခန့်မှန်းကြာချိန် " + fmtMs(total))
        for (i in files.indices) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setBackgroundResource(R.drawable.bg_input)
            row.setPadding(dp(10), dp(6), dp(6), dp(6))
            val nm = TextView(this)
            nm.text = (i + 1).toString() + ". " + names[i] + (if (durs[i] > 0) "  [" + fmtMs(durs[i]) + "]" else "")
            nm.textSize = 11f
            nm.setTextColor(Color.parseColor("#F2F5FF"))
            nm.setSingleLine(true)
            nm.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            val np = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(nm, np)

            val up = TextView(this)
            up.text = "⬆"
            up.textSize = 14f
            up.setTextColor(Color.parseColor("#3DE8F8"))
            up.setPadding(dp(8), dp(4), dp(8), dp(4))
            up.setOnClickListener {
                if (i > 0) {
                    swap(i, i - 1)
                    refreshList()
                }
            }
            row.addView(up)

            val down = TextView(this)
            down.text = "⬇"
            down.textSize = 14f
            down.setTextColor(Color.parseColor("#3DE8F8"))
            down.setPadding(dp(8), dp(4), dp(8), dp(4))
            down.setOnClickListener {
                if (i < files.size - 1) {
                    swap(i, i + 1)
                    refreshList()
                }
            }
            row.addView(down)

            val rm = TextView(this)
            rm.text = "✕"
            rm.textSize = 15f
            rm.setTextColor(Color.parseColor("#FF5C5C"))
            rm.setPadding(dp(10), dp(4), dp(10), dp(4))
            rm.setOnClickListener {
                files.removeAt(i)
                names.removeAt(i)
                durs.removeAt(i)
                refreshList()
            }
            row.addView(rm)

            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.topMargin = dp(6)
            listBox.addView(row, rp)
        }
    }

    private fun swap(a: Int, b: Int) {
        val f = files[a]; files[a] = files[b]; files[b] = f
        val n = names[a]; names[a] = names[b]; names[b] = n
        val d = durs[a]; durs[a] = durs[b]; durs[b] = d
    }

    private fun startMerge() {
        if (merging) return
        if (files.size < 2) {
            Toast.makeText(this, "Video အနည်းဆုံး ၂ ခု ရွေးပေးပါ", Toast.LENGTH_LONG).show()
            return
        }
        merging = true
        mergeBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        var total = 0L
        durs.forEach { total += it }
        val totalTxt = fmtMs(total)
        statusText.text = "ပေါင်းနေသည်... (" + files.size + " clips ≈ " + totalTxt + ")"
        lifecycleScope.launch {
            try {
                val out = File(cacheDir, "merged_out.mp4")
                if (out.exists()) out.delete()
                withContext(Dispatchers.Main) { mergeClips(files, out) }
                statusText.text = "Downloads ထဲ သိမ်းနေသည်..."
                val name = "MERGED_" + System.currentTimeMillis() + ".mp4"
                val ok = withContext(Dispatchers.IO) {
                    DownloadsUtil.saveFile(this@MergeActivity, out, name, "video/mp4")
                }
                out.delete()
                if (ok) {
                    statusText.text = "ပြီးပါပြီ ✓ " + name + " (Downloads) - " + totalTxt
                    Toast.makeText(this@MergeActivity, "ပေါင်းပြီးပါပြီ ✓ Downloads ထဲမှာ သိမ်းပြီးပါပြီ", Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "သိမ်းမှု မအောင်မြင်ပါ (storage စစ်ပါ)"
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                statusText.text = "ERROR: " + msg
                AlertDialog.Builder(this@MergeActivity)
                    .setTitle("Merge မအောင်မြင်ပါ")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            } finally {
                merging = false
                runOnUiThread {
                    mergeBtn.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun mergeClips(clips: List<File>, out: File) {
        val items = ArrayList<EditedMediaItem>()
        clips.forEach { f ->
            val mi = MediaItem.Builder().setUri(Uri.fromFile(f)).build()
            items.add(EditedMediaItem.Builder(mi).build())
        }
        val seq = EditedMediaItemSequence(ImmutableList.copyOf(items))
        val composition = Composition.Builder(seq).build()
        val transformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    mergeDone?.invoke(null)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    mergeDone?.invoke(exportException)
                }
            })
            .build()
        suspendCancellableCoroutine { cont ->
            mergeDone = { err: ExportException? ->
                mergeDone = null
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

    private fun clearList() {
        val dir = File(filesDir, "merge")
        dir.listFiles()?.forEach { f -> f.delete() }
        files.clear()
        names.clear()
        durs.clear()
        refreshList()
        Toast.makeText(this, "List ဖျက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
    }
}
