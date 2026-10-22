package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.recap.DownloadsUtil
import com.gikoko.recap.recap.RecapConfig
import com.gikoko.recap.recap.RecapPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProcessingActivity : AppCompatActivity() {

    private lateinit var stepText: TextView
    private lateinit var logText: TextView
    private lateinit var timeText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var bar: NeonProgressView
    private var busy = false
    private var lastProg = 0
    private var startAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            timeText.text = "⏱ " + elapsed()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_processing)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startAt = SystemClock.elapsedRealtime()
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        stepText = TextView(this)
        logText = TextView(this)
        timeText = TextView(this)
        build(root)
        handler.post(ticker)
        runPipeline()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun elapsed(): String {
        val el = (SystemClock.elapsedRealtime() - startAt) / 1000L
        return String.format("%02d:%02d", el / 60, el % 60)
    }

    private fun addLog(s: String) {
        logText.append("[" + elapsed() + "] " + s + "\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setProg(p: Int) {
        if (p < lastProg) return
        lastProg = p
        if (p == 18) {
            bar.setProgressSlow(43, 75000L)
        } else if (p == 68 || p == 74) {
            bar.setProgressSlow(94, 90000L)
        } else {
            bar.setProgress(p)
        }
    }

    private fun runPipeline() {
        if (busy) return
        busy = true
        val saved = Prefs.apiKey(this)
        val apiKey = if (saved.isNotEmpty()) saved else (intent.getStringExtra("api_key") ?: "")
        val cfg = RecapConfig(
            apiKey,
            Prefs.provider(this),
            Prefs.baseUrl(this),
            Prefs.customKey(this),
            Prefs.customModel(this),
            Prefs.model(this),
            Prefs.voice(this),
            Prefs.minutes(this),
            Prefs.captions(this),
            Prefs.lang(this),
            Prefs.segFull(this),
            Prefs.watermark(this),
            Prefs.introOutro(this),
            Prefs.quality(this),
            Prefs.style(this),
            Prefs.speed(this),
            Prefs.customStyle(this),
            Prefs.vertical(this),
            Prefs.extBase(this),
            Prefs.extKey(this),
            Prefs.extModel(this),
            Prefs.transcriptMode(this),
            Prefs.transcriptFallback(this)
        )
        val source = intent.getStringExtra("source") ?: "youtube"
        val url = intent.getStringExtra("url") ?: ""
        val path = intent.getStringExtra("path") ?: ""
        lifecycleScope.launch {
            try {
                addLog("Engine start - provider: " + cfg.provider + ", model: " + if (cfg.provider == "custom") cfg.customModel else cfg.model)
                val out = if (source == "file") {
                    RecapPipeline.runFromFile(
                        this@ProcessingActivity,
                        File(path),
                        cfg,
                        { s -> runOnUiThread { stepText.text = s } },
                        { s -> runOnUiThread { addLog(s) } },
                        { p -> runOnUiThread { setProg(p) } }
                    )
                } else {
                    RecapPipeline.runFromYouTube(
                        this@ProcessingActivity,
                        url,
                        cfg,
                        { s -> runOnUiThread { stepText.text = s } },
                        { s -> runOnUiThread { addLog(s) } },
                        { p -> runOnUiThread { setProg(p) } }
                    )
                }
                stepText.text = "Recap သိမ်းနေသည်..."
                setProg(98)
                addLog("Recap video ready ✓")
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val srcName = if (source == "file") File(path).name else url
                val archiveVideo = withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "recap_archive/" + stamp)
                    dir.mkdirs()
                    val v = File(dir, "recap.mp4")
                    out.video.copyTo(v, overwrite = true)
                    val s = File(dir, "recap.srt")
                    if (out.srt.exists()) out.srt.copyTo(s, overwrite = true)
                    val meta = JSONObject()
                    meta.put("title", out.title)
                    meta.put("date", stamp)
                    meta.put("source", srcName)
                    meta.put("summary", out.summary)
                    File(dir, "meta.json").writeText(meta.toString())
                    val thumb = File(dir, "thumb.jpg")
                    runCatching {
                        val mmr = android.media.MediaMetadataRetriever()
                        mmr.setDataSource(v.absolutePath)
                        val bm = mmr.getFrameAtTime(1000000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (bm != null) {
                            thumb.outputStream().use { bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                            bm.recycle()
                        }
                        mmr.release()
                    }
                    v
                }
                addLog("Archive saved - " + archiveVideo.name)
                if (Prefs.autosave(this@ProcessingActivity)) {
                    withContext(Dispatchers.IO) {
                        DownloadsUtil.saveRecap(this@ProcessingActivity, archiveVideo, out.srt)
                    }
                    addLog("Download folder ထဲ auto-save ✓")
                }
                setProg(100)
                addLog("Done - result screen ပွင့်နေသည် ✓")
                val i = Intent(this@ProcessingActivity, ResultActivity::class.java)
                i.putExtra("video", archiveVideo.absolutePath)
                i.putExtra("srt", out.srt.absolutePath)
                i.putExtra("title", out.title)
                i.putExtra("summary", out.summary)
                i.putExtra("thumb", File(archiveVideo.parentFile, "thumb.jpg").absolutePath)
                startActivity(i)
                finish()
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                runOnUiThread {
                    addLog("ERROR: " + msg)
                    stepText.text = "မအောင်မြင်ပါ"
                    AlertDialog.Builder(this@ProcessingActivity)
                        .setTitle("Recap မအောင်မြင်ပါ")
                        .setMessage(msg)
                        .setPositiveButton("ပြန်သွားမည်") { d, _ -> finish() }
                        .setNeutralButton("ထပ်စမ်းမည်") { d, _ ->
                            busy = false
                            lastProg = 0
                            bar.setProgress(0)
                            addLog("--- retry ---")
                            runPipeline()
                        }
                        .setNegativeButton("အသေးစိတ်ကြည့်မည်") { d, _ -> d.dismiss() }
                        .show()
                }
            }
        }
    }

    private fun build(root: LinearLayout) {
        val title = TextView(this)
        title.text = "RECAP ENGINE"
        title.setTextColor(Color.parseColor("#FFC940"))
        title.textSize = 20f
        title.gravity = Gravity.CENTER
        title.setTypeface(null, Typeface.BOLD)
        title.letterSpacing = 0.2f
        val tp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tp.setMargins(0, dp(56), 0, dp(4))
        root.addView(title, tp)

        timeText.text = "⏱ 00:00"
        timeText.setTextColor(Color.parseColor("#8A93B2"))
        timeText.textSize = 12f
        timeText.gravity = Gravity.CENTER
        timeText.letterSpacing = 0.1f
        val ttp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(timeText, ttp)

        bar = NeonProgressView(this)
        val bp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22))
        bp.setMargins(dp(28), dp(18), dp(28), 0)
        root.addView(bar, bp)

        stepText.text = "စတင်နေသည်..."
        stepText.setTextColor(Color.parseColor("#3DE8F8"))
        stepText.textSize = 14f
        stepText.gravity = Gravity.CENTER
        stepText.setPadding(dp(16), dp(10), dp(16), dp(8))
        root.addView(stepText)

        val logTitle = TextView(this)
        logTitle.text = "PROCESS LOG"
        logTitle.setTextColor(Color.parseColor("#3DE8F8"))
        logTitle.textSize = 11f
        logTitle.letterSpacing = 0.2f
        logTitle.gravity = Gravity.CENTER
        val ltp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ltp.topMargin = dp(10)
        root.addView(logTitle, ltp)

        logScroll = ScrollView(this)
        logScroll.setBackgroundResource(R.drawable.bg_input)
        logText.setTextColor(Color.parseColor("#A7B6DC"))
        logText.textSize = 11f
        logText.typeface = Typeface.MONOSPACE
        logText.setPadding(dp(14), dp(12), dp(14), dp(12))
        logScroll.addView(logText)
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        sp.setMargins(dp(16), dp(8), dp(16), dp(16))
        root.addView(logScroll, sp)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
