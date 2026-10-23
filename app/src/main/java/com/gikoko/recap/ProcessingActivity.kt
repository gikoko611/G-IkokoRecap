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
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.recap.DownloadsUtil
import com.gikoko.recap.recap.RecapConfig
import com.gikoko.recap.recap.RecapOutput
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
    private var lastActivityAt = 0L
    private var stallWarns = 0
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            timeText.text = "⏱ " + elapsed()
            checkStall()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun checkStall() {
        if (!busy || lastActivityAt == 0L) return
        val idle = (SystemClock.elapsedRealtime() - lastActivityAt) / 1000L
        if (idle > 150L && stallWarns < 2) {
            stallWarns = 2
            addLog("⚠ STALL: " + stepText.text + " - ၂ မိနစ်ခွဲကြာ တုံ့ပြန်မမြင်ပါ။ server ပိတ်နေခြင်း / queue ရှည်ခြင်း / network အားနည်းခြင်း ဖြစ်နိုင်ပါတယ်။ ခဏဆက်စောင့်ပါ၊ ဒါမှမဟုတ် app ပိတ်ပြီး ပြန်စမ်းကြည့်ပါ။")
        } else if (idle > 45L && stallWarns < 1) {
            stallWarns = 1
            addLog("⚠ WAIT: " + stepText.text + " - 45 စက္ကန့်ကြာ တုံ့ပြန်မမြင်ပါ (server busy / queue ဖြစ်နိုင်သည်) - ဆက်စောင့်ဆိုင်းနေသည်...")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open processing")
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
        lastActivityAt = SystemClock.elapsedRealtime()
        stallWarns = 0
        val line = "[" + elapsed() + "] " + s + "\n"
        if (s.startsWith("ERROR")) {
            val ss = android.text.SpannableString(line)
            ss.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#FF5C5C")), 0, line.length, 0)
            logText.append(ss)
        } else {
            logText.append(line)
        }
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copyLog() {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("recap_log", logText.text.toString()))
        android.widget.Toast.makeText(this, L.s(this, "Log ကူးယူပြီး ✓ - ဘယ်နေရာမှာမဆို paste လုပ်လို့ရပါပြီ", "Log copied ✓ - ready to paste"), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun setProg(p: Int) {
        lastActivityAt = SystemClock.elapsedRealtime()
        stallWarns = 0
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
        lastActivityAt = SystemClock.elapsedRealtime()
        stallWarns = 0
        val queueMode = intent.hasExtra("queue")
        val queuePaths = ArrayList<String>()
        if (queueMode) {
            try {
                val qArr = org.json.JSONArray(intent.getStringExtra("queue") ?: "[]")
                for (i in 0 until qArr.length()) queuePaths.add(qArr.getString(i))
            } catch (e: Exception) {
            }
        }
        val queueIdx = intent.getIntExtra("queue_idx", 0)
        val queueSeries = intent.getBooleanExtra("queue_series", false)
        val prevSummary = intent.getStringExtra("prev_summary") ?: ""
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
            Prefs.transcriptFallback(this),
            Prefs.wmSize(this),
            Prefs.wmPos(this),
            Prefs.wmLogo(this),
            Prefs.theme(this),
            if (queueMode) false else Prefs.planConfirm(this),
            Prefs.shorts(this),
            Prefs.podcast(this),
            Prefs.dialogue(this),
            Prefs.voice2(this),
            Prefs.bgmOn(this),
            Prefs.bgmPath(this),
            Prefs.bgmVol(this).toDouble(),
            if (queueMode && queueSeries) prevSummary else ""
        )
        val source = intent.getStringExtra("source") ?: "youtube"
        val url = intent.getStringExtra("url") ?: ""
        val path = intent.getStringExtra("path") ?: ""
        val planPath = intent.getStringExtra("plan_path") ?: ""
        val textPath = intent.getStringExtra("text_path") ?: ""
        lifecycleScope.launch {
            try {
                addLog("Engine start - provider: " + cfg.provider + ", model: " + if (cfg.provider == "custom") cfg.customModel else cfg.model)
                if (queueMode) {
                    addLog("QUEUE mode: item " + (queueIdx + 1) + "/" + queuePaths.size + (if (queueSeries) " [SERIES LINK]" else ""))
                    if (queueSeries && prevSummary.isNotEmpty()) addLog("SERIES: အရင်ပိုင်းအဆုံးကို ချိတ်ဆက်ပြောပါမယ်")
                    if (Prefs.planConfirm(this@ProcessingActivity)) addLog("PLAN EDITOR - QUEUE mode မှာ ခဏ ပိတ်ထားပါမယ်")
                }
                val out: RecapOutput
                if (planPath.isNotEmpty() && File(planPath).exists()) {
                    addLog("Confirmed plan loaded - rendering...")
                    val loaded = GeminiClient.planFromJson(File(planPath).readText())
                    out = if (source == "file") {
                        RecapPipeline.renderFromFile(
                            this@ProcessingActivity,
                            File(path),
                            loaded.first,
                            cfg,
                            loaded.second,
                            { s -> runOnUiThread { stepText.text = s } },
                            { s -> runOnUiThread { addLog(s) } },
                            { p -> runOnUiThread { setProg(p) } }
                        )
                    } else {
                        RecapPipeline.renderYouTube(
                            this@ProcessingActivity,
                            loaded.first,
                            cfg,
                            { s -> runOnUiThread { stepText.text = s } },
                            { s -> runOnUiThread { addLog(s) } },
                            { p -> runOnUiThread { setProg(p) } }
                        )
                    }
                } else if (cfg.planConfirm && source != "text") {
                    val planFile = File(cacheDir, "plan_confirm.json")
                    if (source == "file") {
                        val pf = RecapPipeline.planFromFile(
                            this@ProcessingActivity,
                            File(path),
                            cfg,
                            { s -> runOnUiThread { stepText.text = s } },
                            { s -> runOnUiThread { addLog(s) } },
                            { p -> runOnUiThread { setProg(p) } }
                        )
                        planFile.writeText(GeminiClient.planToJson(pf.first, pf.second))
                    } else {
                        val p1 = RecapPipeline.planFromYouTube(
                            this@ProcessingActivity,
                            url,
                            cfg,
                            { s -> runOnUiThread { stepText.text = s } },
                            { s -> runOnUiThread { addLog(s) } },
                            { p -> runOnUiThread { setProg(p) } }
                        )
                        planFile.writeText(GeminiClient.planToJson(p1, true))
                    }
                    addLog("Plan ready - opening PLAN EDITOR...")
                    val ci = Intent(this@ProcessingActivity, ConfirmActivity::class.java)
                    ci.putExtra("plan_path", planFile.absolutePath)
                    ci.putExtra("source", source)
                    ci.putExtra("url", url)
                    ci.putExtra("path", path)
                    startActivity(ci)
                    finish()
                    return@launch
                } else {
                    out = if (source == "text") {
                        val txt = withContext(Dispatchers.IO) { if (textPath.isNotEmpty()) File(textPath).readText() else "" }
                        if (txt.length < 40) throw java.io.IOException(L.s(this@ProcessingActivity, "စာသား မရနိုင်ပါ", "Text not available"))
                        addLog("TEXT-TO-RECAP: " + txt.length + " chars")
                        if (cfg.extKey.isEmpty()) throw java.io.IOException(L.s(this@ProcessingActivity, "TEXT mode မှာ TRANSCRIPT MODE provider KEY လိုပါတယ် - TOOLS ထဲမှာ ထည့်ပါ", "Transcript-mode provider key required for text mode"))
                        stepText.text = L.s(this@ProcessingActivity, "AI နဲ့ ခြုံငုံနေသည်...", "Analyzing text...")
                        setProg(18)
                        val plan = withContext(Dispatchers.IO) { GeminiClient.generatePlanTranscript(cfg.extBase, cfg.extKey, cfg.extModel, txt, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, cfg.customStyle, { s -> runOnUiThread { addLog(s) } }) }
                        Prefs.bumpUsage(this@ProcessingActivity, Prefs.KEY_U_EXT, 1)
                        setProg(45)
                        RecapPipeline.renderYouTube(
                            this@ProcessingActivity,
                            plan,
                            cfg,
                            { s -> runOnUiThread { stepText.text = s } },
                            { s -> runOnUiThread { addLog(s) } },
                            { p -> runOnUiThread { setProg(p) } }
                        )
                    } else if (source == "file") {
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
                }
                stepText.text = L.s(this@ProcessingActivity, "Recap သိမ်းနေသည်...", "Saving recap...")
                setProg(98)
                addLog("Recap video ready ✓")
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val srcName = if (source == "file") File(path).name else if (source == "text") "text_recap" else url
                val archiveVideo = withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "recap_archive/" + stamp)
                    dir.mkdirs()
                    val v = File(dir, "recap.mp4")
                    out.video.copyTo(v, overwrite = true)
                    val s = File(dir, "recap.srt")
                    if (out.srt.exists()) out.srt.copyTo(s, overwrite = true)
                    if (out.shorts != null && out.shorts.exists()) out.shorts.copyTo(File(dir, "short.mp4"), overwrite = true)
                    if (out.podcast != null && out.podcast.exists()) out.podcast.copyTo(File(dir, "podcast.m4a"), overwrite = true)
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
                if (Prefs.autosave(this@ProcessingActivity) || queueMode) {
                    withContext(Dispatchers.IO) {
                        DownloadsUtil.saveRecap(this@ProcessingActivity, archiveVideo, out.srt)
                    }
                    addLog("Download folder ထဲ auto-save ✓")
                }
                setProg(100)
                if (queueMode && queueIdx + 1 < queuePaths.size) {
                    addLog("✅ QUEUE item " + (queueIdx + 1) + "/" + queuePaths.size + " ပြီးပါပြီ - နောက် video ကို ဆက်လုပ်နေသည်...")
                    val ni = Intent(this@ProcessingActivity, ProcessingActivity::class.java)
                    ni.putExtra("source", "file")
                    ni.putExtra("path", queuePaths[queueIdx + 1])
                    ni.putExtra("queue", intent.getStringExtra("queue"))
                    ni.putExtra("queue_idx", queueIdx + 1)
                    ni.putExtra("queue_series", queueSeries)
                    if (queueSeries) {
                        var ps = out.summary
                        val cIdx = ps.indexOf("\n\nCHAPTERS")
                        if (cIdx > 0) ps = ps.substring(0, cIdx)
                        if (ps.length > 700) ps = ps.substring(ps.length - 700)
                        ni.putExtra("prev_summary", ps)
                    }
                    startActivity(ni)
                    finish()
                    return@launch
                }
                if (queueMode) addLog("QUEUE အားလုံး ပြီးပါပြီ ✓ (" + queuePaths.size + " videos)")
                addLog("Done - result screen ပွင့်နေသည် ✓")
                val i = Intent(this@ProcessingActivity, ResultActivity::class.java)
                i.putExtra("video", archiveVideo.absolutePath)
                i.putExtra("srt", out.srt.absolutePath)
                i.putExtra("title", out.title)
                i.putExtra("summary", out.summary)
                i.putExtra("thumb", File(archiveVideo.parentFile, "thumb.jpg").absolutePath)
                val shortF = File(archiveVideo.parentFile, "short.mp4")
                if (shortF.exists()) i.putExtra("short", shortF.absolutePath)
                val podF = File(archiveVideo.parentFile, "podcast.m4a")
                if (podF.exists()) i.putExtra("podcast", podF.absolutePath)
                if (source == "youtube") i.putExtra("source_url", url)
                startActivity(i)
                finish()
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                runOnUiThread {
                    addLog("ERROR: " + msg)
                    stepText.text = L.s(this@ProcessingActivity, "မအောင်မြင်ပါ", "Failed")
                    val db = AlertDialog.Builder(this@ProcessingActivity)
                        .setTitle(L.s(this@ProcessingActivity, "Recap မအောင်မြင်ပါ", "Recap failed"))
                        .setMessage(msg)
                    if (queueMode && queueIdx + 1 < queuePaths.size) {
                        db.setPositiveButton(L.s(this@ProcessingActivity, "ဒီချက်ကို ကျော်ပြီး နောက်ချက် ဆက်လုပ်မည်", "Skip to next video")) { d, _ ->
                            val ni = Intent(this@ProcessingActivity, ProcessingActivity::class.java)
                            ni.putExtra("source", "file")
                            ni.putExtra("path", queuePaths[queueIdx + 1])
                            ni.putExtra("queue", intent.getStringExtra("queue"))
                            ni.putExtra("queue_idx", queueIdx + 1)
                            ni.putExtra("queue_series", queueSeries)
                            startActivity(ni)
                            finish()
                        }
                    } else {
                        db.setPositiveButton(L.s(this@ProcessingActivity, "ပြန်သွားမည်", "Go back")) { d, _ -> finish() }
                    }
                    db.setNeutralButton(L.s(this@ProcessingActivity, "ထပ်စမ်းမည်", "Retry")) { d, _ ->
                        busy = false
                        lastProg = 0
                        bar.setProgress(0)
                        addLog("--- retry ---")
                        runPipeline()
                    }
                    db.setNegativeButton(L.s(this@ProcessingActivity, "အသေးစိတ်ကြည့်မည်", "See details")) { d, _ -> d.dismiss() }
                    db.show()
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

        stepText.text = L.s(this@ProcessingActivity, "စတင်နေသည်...", "Starting...")
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
        logText.setTextIsSelectable(true)
        logText.setPadding(dp(14), dp(12), dp(14), dp(12))
        logScroll.addView(logText)
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        sp.setMargins(dp(16), dp(8), dp(16), dp(8))
        root.addView(logScroll, sp)

        val copyBtn = android.widget.Button(this)
        copyBtn.text = L.s(this@ProcessingActivity, "📋 LOG ကူးယူမည်", "📋 COPY LOG")
        copyBtn.isAllCaps = false
        copyBtn.textSize = 12f
        copyBtn.setTextColor(Color.parseColor("#3DE8F8"))
        copyBtn.setBackgroundResource(R.drawable.bg_chip_selected)
        copyBtn.setOnClickListener { copyLog() }
        val cbp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cbp.setMargins(dp(16), 0, dp(16), dp(16))
        root.addView(copyBtn, cbp)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
