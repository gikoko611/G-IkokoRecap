package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.net.EdgeTtsClient
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.net.Tts
import com.gikoko.recap.recap.WavUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ToolsActivity : AppCompatActivity() {

    private val voices = listOf("Sulafat", "Vindemiatrix", "Erinome", "Aoede", "Kore", "Leda", "Zephyr", "Despina", "Charon", "Iapetus", "Achird", "Orus", "Puck", "Fenrir", "Algenib", "Rasalgethi")
    private val lengths = listOf("Auto", "1 min", "3 min", "5 min")
    private val minutesOf = intArrayOf(0, 1, 3, 5)
    private val langs = listOf("မြန်မာ", "English")
    private val langOf = listOf("my", "en")

    private lateinit var edgeVoiceGroup: ChipGroup
    private lateinit var extKeyInput: EditText
    private lateinit var extModelInput: EditText
    private lateinit var extBaseInput: EditText
    private lateinit var freeModelStatus: TextView
    private lateinit var freeModelsGroup: ChipGroup
    private lateinit var baseUrlInput: EditText
    private lateinit var customKeyInput: EditText
    private lateinit var customModelsGroup: ChipGroup
    private lateinit var modelStatus: TextView
    private lateinit var usageText: TextView
    private lateinit var weekText: TextView
    private var testPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open tools")
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
        refreshUsage()
        updateEngineVis()
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

    private fun switchRow(label: String, key: String, def: Boolean): SwitchCompat {
        val sw = SwitchCompat(this)
        sw.text = label
        sw.textSize = 14f
        sw.setTextColor(Color.parseColor("#F2F5FF"))
        sw.isChecked = Prefs.bool(this, key, def)
        sw.setOnCheckedChangeListener { _, checked -> Prefs.saveBool(this, key, checked) }
        return sw
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

    private fun gridBox(icon: String, label: String, onClick: () -> Unit): LinearLayout {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER
        b.setBackgroundResource(R.drawable.bg_mode_card)
        b.setPadding(dp(12), dp(16), dp(12), dp(16))
        val ic = TextView(this)
        ic.text = icon
        ic.textSize = 24f
        ic.gravity = Gravity.CENTER
        b.addView(ic)
        val t = TextView(this)
        t.text = label
        t.textSize = 12f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Color.parseColor("#FFC940"))
        t.gravity = Gravity.CENTER
        val tp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tp.topMargin = dp(6)
        b.addView(t, tp)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun gridRow(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(dp(12), dp(6), dp(12), dp(6))
        r.layoutParams = p
        return r
    }

    private fun addGridBox(row: LinearLayout, b: LinearLayout) {
        val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(dp(4), 0, dp(4), 0)
        row.addView(b, p)
    }

    private fun updateEngineVis() {
        if (!::edgeVoiceGroup.isInitialized) return
        val edge = Prefs.ttsEngine(this) == "edge"
        edgeVoiceGroup.visibility = if (edge) View.VISIBLE else View.GONE
    }

    private fun rebuildCustomModels(models: List<String>) {
        customModelsGroup.removeAllViews()
        val saved = Prefs.customModel(this)
        models.forEachIndexed { i, m ->
            val ch = chipOf(m)
            ch.id = 50000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.save(this, Prefs.KEY_CUSTOM_MODEL, m)
            }
            customModelsGroup.addView(ch)
        }
        if (models.isNotEmpty()) {
            val idx = models.indexOf(saved)
            customModelsGroup.check(if (idx >= 0) 50000 + idx else 50000)
        }
    }

    private fun rebuildFreeModels(models: List<String>) {
        freeModelsGroup.removeAllViews()
        val saved = Prefs.extModel(this)
        models.forEachIndexed { i, m ->
            val ch = chipOf(m)
            ch.id = 60200 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    Prefs.save(this, Prefs.KEY_EXT_MODEL, m)
                    if (::extModelInput.isInitialized) extModelInput.setText(m)
                    Toast.makeText(this, "Model ရွေးချယ်ပြီး - " + m, Toast.LENGTH_SHORT).show()
                }
            }
            freeModelsGroup.addView(ch)
        }
        if (models.isNotEmpty()) {
            val idx = models.indexOf(saved)
            freeModelsGroup.check(if (idx >= 0) 60200 + idx else 60200)
        }
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
        ht.text = "TOOLS"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val g1 = gridRow()
        addGridBox(g1, gridBox("🎬", "RECAP STUDIO") { startActivity(Intent(this, StudioActivity::class.java)) })
        addGridBox(g1, gridBox("🎙", "VOICE LAB") { startActivity(Intent(this, VoiceLabActivity::class.java)) })
        root.addView(g1)

        val g2 = gridRow()
        addGridBox(g2, gridBox("🕘", "HISTORY") { startActivity(Intent(this, HistoryActivity::class.java)) })
        addGridBox(g2, gridBox("🐞", "CRASH LOG") {
            val logTxt = DebugLog.read(this)
            AlertDialog.Builder(this)
                .setTitle("CRASH LOG")
                .setMessage(if (logTxt.length > 3500) logTxt.take(3500) else logTxt)
                .setPositiveButton("SHARE") { _, _ ->
                    val send = Intent(Intent.ACTION_SEND)
                    send.type = "text/plain"
                    send.putExtra(Intent.EXTRA_TEXT, logTxt)
                    startActivity(Intent.createChooser(send, "Crash Log"))
                }
                .setNegativeButton("ဖျက်မည်") { _, _ -> DebugLog.clear(this) }
                .setNeutralButton("OK", null)
                .show()
        })
        root.addView(g2)

        val g3 = gridRow()
        addGridBox(g3, gridBox("🪄", "WM REMOVER") { startActivity(Intent(this, WatermarkActivity::class.java)) })
        addGridBox(g3, gridBox("💬", "SRT EXPORT") { startActivity(Intent(this, SrtActivity::class.java)) })
        root.addView(g3)

        val g4 = gridRow()
        addGridBox(g4, gridBox("🖼", "THUMB MAKER") { startActivity(Intent(this, ThumbActivity::class.java)) })
        addGridBox(g4, gridBox("🌐", "AI STUDIO") { startActivity(Intent(this, AiStudioActivity::class.java)) })
        root.addView(g4)

        val g5 = gridRow()
        addGridBox(g5, gridBox("🎬", "VIDEO QUEUE") { startActivity(Intent(this, QueueActivity::class.java)) })
        addGridBox(g5, gridBox("🎞", "MERGER") { startActivity(Intent(this, MergeActivity::class.java)) })
        root.addView(g5)

        val g6 = gridRow()
        addGridBox(g6, gridBox("🖼", "GALLERY") { startActivity(Intent(this, GalleryActivity::class.java)) })
        addGridBox(g6, gridBox("🧹", "CLEANER") { startActivity(Intent(this, CleanerActivity::class.java)) })
        root.addView(g6)

        val g7 = gridRow()
        addGridBox(g7, gridBox("🎬", "VIDEO LAB") { startActivity(Intent(this, VideoLabActivity::class.java)) })
        root.addView(g7)

        val g8 = gridRow()
        addGridBox(g8, gridBox("📄", "TEXT RECAP") { startActivity(Intent(this, TextRecapActivity::class.java)) })
        addGridBox(g8, gridBox("🖼", "SLIDESHOW") { startActivity(Intent(this, SlideshowActivity::class.java)) })
        root.addView(g8)

        val g9 = gridRow()
        addGridBox(g9, gridBox("🗣", "VOICE-OVER") { startActivity(Intent(this, VoiceOverActivity::class.java)) })
        addGridBox(g9, gridBox("🎵", "BGM LIBRARY") { startActivity(Intent(this, BgmLibraryActivity::class.java)) })
        root.addView(g9)

        val g10 = gridRow()
        addGridBox(g10, gridBox("🎞", "COMPRESS") { startActivity(Intent(this, CompressActivity::class.java)) })
        addGridBox(g10, gridBox("💬", "SUB BURN") { startActivity(Intent(this, SubtitleBurnActivity::class.java)) })
        root.addView(g10)

        val g11 = gridRow()
        addGridBox(g11, gridBox("🖼", "PORTRAIT") { startActivity(Intent(this, PortraitActivity::class.java)) })
        root.addView(g11)

        val ce = card()
        ce.addView(sectionTitle("TTS ENGINE - အသံထုတ် ယန္တရား"))
        val engG = ChipGroup(this)
        engG.isSingleSelection = true
        engG.isSelectionRequired = true
        listOf("Gemini TTS (Pro)", "Edge TTS (Free)").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 60000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    Prefs.save(this, Prefs.KEY_TTS_ENGINE, if (i == 0) "gemini" else "edge")
                    updateEngineVis()
                }
            }
            engG.addView(ch)
        }
        engG.check(if (Prefs.ttsEngine(this) == "edge") 60001 else 60000)
        ce.addView(engG)
        edgeVoiceGroup = ChipGroup(this)
        edgeVoiceGroup.isSingleSelection = true
        edgeVoiceGroup.isSelectionRequired = true
        val edgeVoices = listOf(EdgeTtsClient.VOICE_MY_FEMALE, EdgeTtsClient.VOICE_MY_MALE)
        listOf("Nilar (မ - Warm)", "Thiha (က - Deep)").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 60100 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.save(this, Prefs.KEY_EDGE_VOICE, edgeVoices[i])
            }
            edgeVoiceGroup.addView(ch)
        }
        val curEv = edgeVoices.indexOf(Prefs.edgeVoice(this))
        edgeVoiceGroup.check(if (curEv >= 0) 60100 + curEv else 60100)
        ce.addView(edgeVoiceGroup, topParams())
        val noteE = note("Edge TTS = Microsoft ရဲ့ အခမဲ့ server - API key မလိုပါဘူး၊ နေ့စဉ် limit လည်း မရှိပါဘူး။ မြန်မာအသံ Nilar (မ) / Thiha (က) ပါဝင်ပါတယ်။ Edge မအောင်မြင်ရင် Gemini ကို auto-fallback လုပ်ပါတယ်။ Gemini TTS က အသံသွယ်ပြီး voice အမျိုးမျိုး ရွေးလို့ရပေမယ့် နေ့စဉ် limit ရှိပါတယ်။")
        ce.addView(noteE, topParams())
        val testVoiceBtn = flatBtn("🔊 TEST VOICE", R.drawable.bg_chip_selected, "#F2F5FF")
        ce.addView(testVoiceBtn, topParams())
        testVoiceBtn.setOnClickListener {
            Toast.makeText(this, "အသံထုတ်နေသည်...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val key = Prefs.apiKey(this@ToolsActivity)
                    val (pcm, rate) = withContext(Dispatchers.IO) {
                        Tts.generate(this@ToolsActivity, key, "မင်္ဂလာပါ။ ဒီက သင်ရွေးထားတဲ့ TTS engine အသံပါ။", Prefs.voice(this@ToolsActivity), Prefs.speed(this@ToolsActivity))
                    }
                    val wav = WavUtil.pcmToWav(pcm, rate)
                    val f = File(cacheDir, "tools_voice_test.wav")
                    withContext(Dispatchers.IO) { f.writeBytes(wav) }
                    testPlayer?.release()
                    testPlayer = MediaPlayer()
                    testPlayer?.setDataSource(f.absolutePath)
                    testPlayer?.prepare()
                    testPlayer?.start()
                } catch (e: Exception) {
                    Toast.makeText(this@ToolsActivity, "မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(ce)

        val ct = card()
        ct.addView(sectionTitle("TRANSCRIPT MODE - FREE TEXT MODELS"))
        ct.addView(switchRow("Gemini quota ကုန်ရင် transcript နဲ့ auto-fallback", Prefs.KEY_TRANSCRIPT_FALLBACK, true))
        ct.addView(switchRow("YouTube recap ကို transcript mode နဲ့ အမြဲလုပ်မည်", Prefs.KEY_TRANSCRIPT_MODE, false), topParams())
        val presetGroup = ChipGroup(this)
        presetGroup.isSingleSelection = true
        val presetNames = listOf("OpenRouter", "Groq", "ModelScope")
        val presetUrls = listOf("https://openrouter.ai/api/v1", "https://api.groq.com/openai/v1", "https://api-inference.modelscope.cn/v1")
        val presetHints = listOf("deepseek/deepseek-chat-v3.1:free", "llama-3.3-70b-versatile", "Qwen/Qwen3.5-27B")
        presetNames.forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 41000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    Prefs.save(this, Prefs.KEY_EXT_BASE, presetUrls[i])
                    if (::extModelInput.isInitialized) extModelInput.hint = "model name (ဥပမာ " + presetHints[i] + ")"
                    if (::extBaseInput.isInitialized) extBaseInput.visibility = View.GONE
                }
            }
            presetGroup.addView(ch)
        }
        val chCustom = chipOf("CUSTOM LINK")
        chCustom.id = 41003
        chCustom.setOnCheckedChangeListener { _, checked ->
            if (checked && ::extBaseInput.isInitialized) {
                extBaseInput.visibility = View.VISIBLE
                if (extBaseInput.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Custom provider link ရိုက်ထည့်ပြီး SAVE နှိပ်ပါ", Toast.LENGTH_LONG).show()
                }
            }
        }
        presetGroup.addView(chCustom)
        val pIdx = presetUrls.indexOf(Prefs.extBase(this))
        if (pIdx >= 0) {
            presetGroup.check(41000 + pIdx)
        } else {
            presetGroup.check(41003)
        }
        ct.addView(presetGroup, topParams())
        extBaseInput = EditText(this)
        extBaseInput.hint = "Custom provider link (ဥပမာ https://api.together.xyz/v1)"
        inputStyle(extBaseInput)
        val savedEb = Prefs.extBase(this)
        if (savedEb.isNotEmpty()) extBaseInput.setText(savedEb)
        extBaseInput.visibility = if (pIdx >= 0) View.GONE else View.VISIBLE
        ct.addView(extBaseInput, topParams())
        extKeyInput = EditText(this)
        extKeyInput.hint = "Fallback provider API key"
        inputStyle(extKeyInput)
        val savedEk = Prefs.extKey(this)
        if (savedEk.isNotEmpty()) extKeyInput.setText(savedEk)
        ct.addView(extKeyInput, topParams())
        extModelInput = EditText(this)
        extModelInput.hint = "model name (ဥပမာ deepseek/deepseek-chat-v3.1:free)"
        inputStyle(extModelInput)
        val savedEm = Prefs.extModel(this)
        if (savedEm.isNotEmpty()) extModelInput.setText(savedEm)
        ct.addView(extModelInput, topParams())
        freeModelStatus = note("🔎 AUTO-FIND နှိပ်ပြီး provider ရဲ့ free model တွေ အလိုအလျောက် ရှာပါ (OpenRouter က key မလိုပါ)။ ရှာတွေ့ရင် chip တွေပေါ်လာမယ် - တစ်ခု ရွေးလိုက်ရင် model ကို auto-save လုပ်ပါမယ်။ ကိုယ်တိုင် ရိုက်ထည့်လို့လည်း ရပါတယ်။")
        ct.addView(freeModelStatus, topParams())
        freeModelsGroup = ChipGroup(this)
        freeModelsGroup.isSingleSelection = true
        ct.addView(freeModelsGroup, topParams())
        val findBtn = flatBtn("🔎 AUTO-FIND FREE MODELS", R.drawable.bg_mode_card, "#C9D2EC")
        ct.addView(findBtn, topParams())
        findBtn.setOnClickListener {
            val bT = extBaseInput.text.toString().trim()
            val b = if (bT.isNotEmpty()) GeminiClient.normalizeBase(bT) else Prefs.extBase(this)
            Prefs.save(this, Prefs.KEY_EXT_BASE, b)
            val k = extKeyInput.text.toString().trim()
            Prefs.save(this, Prefs.KEY_EXT_KEY, k)
            freeModelStatus.text = "Free model တွေ ရှာနေသည်..."
            lifecycleScope.launch {
                try {
                    val models = withContext(Dispatchers.IO) { GeminiClient.listCustomModels(b, k) }
                    runOnUiThread {
                        rebuildFreeModels(models)
                        freeModelStatus.text = "Free model " + models.size + " ခု တွေ့ပါတယ် ✓ - တစ်ခု ရွေးပါ"
                    }
                } catch (e: Exception) {
                    runOnUiThread { freeModelStatus.text = "မရနိုင်: " + (e.message ?: "") + " - OpenRouter က key မလိုပါ၊ Groq/ModelScope က key လိုပါတယ်" }
                }
            }
        }
        val rowCT = LinearLayout(this)
        rowCT.orientation = LinearLayout.HORIZONTAL
        ct.addView(rowCT, topParams())
        val saveCT = flatBtn("SAVE", R.drawable.bg_gold_btn, "#0A0A00")
        val testCT = flatBtn("TEST KEY", R.drawable.bg_chip_selected, "#F2F5FF")
        rowCT.addView(saveCT, btnParams())
        rowCT.addView(testCT, btnParams())
        saveCT.setOnClickListener {
            val eb = extBaseInput.text.toString().trim()
            if (eb.isNotEmpty()) Prefs.save(this, Prefs.KEY_EXT_BASE, GeminiClient.normalizeBase(eb))
            Prefs.save(this, Prefs.KEY_EXT_KEY, extKeyInput.text.toString().trim())
            Prefs.save(this, Prefs.KEY_EXT_MODEL, extModelInput.text.toString().trim())
            Toast.makeText(this, "သိမ်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
        }
        testCT.setOnClickListener {
            val bT = extBaseInput.text.toString().trim()
            val b = if (bT.isNotEmpty()) GeminiClient.normalizeBase(bT) else Prefs.extBase(this)
            Prefs.save(this, Prefs.KEY_EXT_BASE, b)
            val k = extKeyInput.text.toString().trim()
            if (k.isEmpty()) {
                Toast.makeText(this, "API key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.save(this, Prefs.KEY_EXT_KEY, k)
            Toast.makeText(this, "စစ်ဆေးနေသည်...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val r = withContext(Dispatchers.IO) { GeminiClient.testKeyCustom(b, k) }
                    Toast.makeText(this@ToolsActivity, "Key မှန်ပါတယ် - " + r, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ToolsActivity, "မမှန်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
        val noteCT = note("Transcript mode က video ထဲမှာ ပြောတဲ့ စကားသား (caption) ကို ဖတ်ပါတယ်။ File mode မှာ Whisper (Groq) နဲ့ အသံကို ဖတ်ပါတယ်။ အသံ (TTS) က TTS ENGINE မှာ ရွေးထားတဲ့ engine နဲ့ ဆက်ထုတ်ပါတယ်။")
        noteCT.setPadding(0, dp(8), 0, 0)
        ct.addView(noteCT)
        root.addView(ct)

        val cc = card()
        cc.addView(sectionTitle("CUSTOM PROVIDER (REST API)"))
        baseUrlInput = EditText(this)
        baseUrlInput.hint = "https://openrouter.ai/api/v1"
        inputStyle(baseUrlInput)
        val savedBase = Prefs.baseUrl(this)
        if (savedBase.isNotEmpty()) baseUrlInput.setText(savedBase)
        cc.addView(baseUrlInput)
        customKeyInput = EditText(this)
        customKeyInput.hint = "Provider API key (openrouter.ai/keys)"
        inputStyle(customKeyInput)
        val savedCk = Prefs.customKey(this)
        if (savedCk.isNotEmpty()) customKeyInput.setText(savedCk)
        cc.addView(customKeyInput, topParams())
        val rowC = LinearLayout(this)
        rowC.orientation = LinearLayout.HORIZONTAL
        cc.addView(rowC, topParams())
        val saveC = flatBtn("SAVE", R.drawable.bg_gold_btn, "#0A0A00")
        val testC = flatBtn("TEST KEY", R.drawable.bg_chip_selected, "#F2F5FF")
        rowC.addView(saveC, btnParams())
        rowC.addView(testC, btnParams())
        saveC.setOnClickListener {
            Prefs.save(this, Prefs.KEY_BASE_URL, baseUrlInput.text.toString().trim())
            Prefs.save(this, Prefs.KEY_CUSTOM_KEY, customKeyInput.text.toString().trim())
            Toast.makeText(this, "သိမ်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
        }
        testC.setOnClickListener {
            val b = baseUrlInput.text.toString().trim()
            val k = customKeyInput.text.toString().trim()
            if (b.isEmpty() || k.isEmpty()) {
                Toast.makeText(this, "Base URL နဲ့ Key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.save(this, Prefs.KEY_BASE_URL, b)
            Prefs.save(this, Prefs.KEY_CUSTOM_KEY, k)
            Toast.makeText(this, "စစ်ဆေးနေသည်...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val r = withContext(Dispatchers.IO) { GeminiClient.testKeyCustom(b, k) }
                    Toast.makeText(this@ToolsActivity, "Key မှန်ပါတယ် - " + r, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ToolsActivity, "မမှန်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
        cc.addView(sectionTitle("FREE MODELS"))
        modelStatus = note("REFRESH နှိပ်ပြီး provider ရဲ့ free model တွေ ရယူပါ")
        cc.addView(modelStatus)
        customModelsGroup = ChipGroup(this)
        customModelsGroup.isSingleSelection = true
        customModelsGroup.isSelectionRequired = true
        cc.addView(customModelsGroup)
        val refreshC = flatBtn("REFRESH - FREE MODELS", R.drawable.bg_mode_card, "#C9D2EC")
        cc.addView(refreshC, topParams())
        refreshC.setOnClickListener {
            val b = baseUrlInput.text.toString().trim()
            val k = customKeyInput.text.toString().trim()
            if (b.isEmpty()) {
                Toast.makeText(this, "Base URL ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.save(this, Prefs.KEY_BASE_URL, b)
            Prefs.save(this, Prefs.KEY_CUSTOM_KEY, k)
            modelStatus.text = "Free model တွေ ရှာနေသည်..."
            lifecycleScope.launch {
                try {
                    val models = withContext(Dispatchers.IO) { GeminiClient.listCustomModels(b, k) }
                    runOnUiThread {
                        rebuildCustomModels(models)
                        modelStatus.text = "Free model " + models.size + " ခု တွေ့ပါတယ် - video input ပါတဲ့ model (ဥပမာ gemma-4) ရွေးပါ"
                    }
                } catch (e: Exception) {
                    runOnUiThread { modelStatus.text = "မရနိုင်: " + (e.message ?: "") }
                }
            }
        }
        root.addView(cc)

        val cv = card()
        cv.addView(sectionTitle("အသံ - DEFAULT VOICE (GEMINI)"))
        val vg = ChipGroup(this)
        vg.isSingleSelection = true
        vg.isSelectionRequired = true
        voices.forEachIndexed { i, v ->
            val ch = chipOf(v)
            ch.id = 11000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.save(this, Prefs.KEY_VOICE, v)
            }
            vg.addView(ch)
        }
        val curVoice = Prefs.voice(this)
        val vi = voices.indexOf(curVoice)
        vg.check(if (vi >= 0) 11000 + vi else 11000)
        cv.addView(vg)
        val noteV = note("Sulafat (warm female) က မြန်မာ narration နဲ့ အနီးစပ်ဆုံး - default ပါ။ ဒီ voice တွေက Gemini TTS ရွေးထားတဲ့အခါပဲ သုံးပါတယ်။ Edge TTS သုံးရင် Nilar/Thiha ရွေးပါ။")
        cv.addView(noteV, topParams())
        root.addView(cv)

        val cw = card()
        cw.addView(sectionTitle("WATERMARK DESIGNER"))
        val wmPosVals = listOf("tl", "tr", "bl", "br")
        val wpG = ChipGroup(this)
        wpG.isSingleSelection = true
        wpG.isSelectionRequired = true
        listOf("TOP LEFT", "TOP RIGHT", "BOTTOM LEFT", "BOTTOM RIGHT").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 42000 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) Prefs.save(this, Prefs.KEY_WM_POS, wmPosVals[i]) }
            wpG.addView(ch)
        }
        val curPosIdx = wmPosVals.indexOf(Prefs.wmPos(this))
        wpG.check(if (curPosIdx >= 0) 42000 + curPosIdx else 42001)
        cw.addView(wpG)
        cw.addView(sectionTitle("LOGO SIZE"))
        val wmSizeVals = listOf(60, 100, 140, 180)
        val wsG = ChipGroup(this)
        wsG.isSingleSelection = true
        wsG.isSelectionRequired = true
        listOf("SMALL", "MEDIUM", "LARGE", "XL").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 42100 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) Prefs.saveInt(this, Prefs.KEY_WM_SIZE, wmSizeVals[i]) }
            wsG.addView(ch)
        }
        val curSizeIdx = wmSizeVals.indexOf(Prefs.wmSize(this))
        wsG.check(if (curSizeIdx >= 0) 42100 + curSizeIdx else 42101)
        cw.addView(wsG)
        val rowW = LinearLayout(this)
        rowW.orientation = LinearLayout.HORIZONTAL
        val pickLogo = flatBtn("PICK LOGO", R.drawable.bg_chip_selected, "#F2F5FF")
        val clearLogo = flatBtn("CLEAR LOGO", R.drawable.bg_mode_card, "#C9D2EC")
        rowW.addView(pickLogo, btnParams())
        rowW.addView(clearLogo, btnParams())
        cw.addView(rowW, topParams())
        pickLogo.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 77)
        }
        clearLogo.setOnClickListener {
            File(filesDir, "wm_logo.jpg").delete()
            Prefs.save(this, Prefs.KEY_WM_LOGO, "")
            Toast.makeText(this, "Logo ဖျက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
        }
        val wmNote = note("ကိုယ်ပိုင် logo (photo) ရွေးလို့ရတယ် - အရွယ်အစား ပြောင်းလို့ရတယ်။ မရွေးရင် GIkoko text watermark ပဲ ပေါ်မယ်။")
        wmNote.setPadding(0, dp(8), 0, 0)
        cw.addView(wmNote)
        root.addView(cw)

        val cth = card()
        cth.addView(sectionTitle("INTRO / OUTRO THEME"))
        val thVals = listOf("gold", "blue", "purple", "green", "red")
        val thG = ChipGroup(this)
        thG.isSingleSelection = true
        thG.isSelectionRequired = true
        listOf("GOLD", "BLUE", "PURPLE", "GREEN", "RED").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 42200 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) Prefs.save(this, Prefs.KEY_THEME, thVals[i]) }
            thG.addView(ch)
        }
        val curThemeIdx = thVals.indexOf(Prefs.theme(this))
        thG.check(if (curThemeIdx >= 0) 42200 + curThemeIdx else 42200)
        cth.addView(thG)
        root.addView(cth)

        val cu = card()
        cu.addView(sectionTitle("📊 API USAGE TRACKER"))
        usageText = TextView(this)
        usageText.setTextColor(Color.parseColor("#C9D2EC"))
        usageText.textSize = 13f
        cu.addView(usageText, topParams())
        weekText = TextView(this)
        weekText.setTextColor(Color.parseColor("#8A93B2"))
        weekText.textSize = 11f
        weekText.setTypeface(Typeface.MONOSPACE)
        cu.addView(weekText, topParams())
        val resetU = flatBtn("RESET USAGE", R.drawable.bg_mode_card, "#C9D2EC")
        cu.addView(resetU, topParams())
        resetU.setOnClickListener {
            Prefs.resetUsage(this)
            refreshUsage()
            Toast.makeText(this, "Reset လုပ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
        }
        val noteU = note("P = Plan (Gemini/Custom ခြုံငုံ)  •  T = TTS segments  •  X = Text (transcript plan)  •  A = ASR (Whisper)။ နေ့စဉ် သုံးစွဲမှုကို 7 ရက် bar ပုံစံ ပြပါတယ်။")
        cu.addView(noteU, topParams())
        root.addView(cu)

        val capp = card()
        capp.addView(sectionTitle("APP LANGUAGE - UI"))
        val uiG = ChipGroup(this)
        uiG.isSingleSelection = true
        uiG.isSelectionRequired = true
        listOf("မြန်မာ", "ENGLISH").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 42300 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    val v = if (i == 0) "mm" else "en"
                    if (Prefs.uiLang(this) != v) {
                        Prefs.save(this, Prefs.KEY_UI_LANG, v)
                        recreate()
                    }
                }
            }
            uiG.addView(ch)
        }
        uiG.check(if (Prefs.uiLang(this) == "en") 42301 else 42300)
        capp.addView(uiG)
        capp.addView(switchRow("Recap မစခင် PLAN တည်းဖြတ်မည် (PLAN EDITOR)", Prefs.KEY_PLAN_CONFIRM, true), topParams())
        root.addView(capp)

        val c4 = card()
        c4.addView(sectionTitle("RECAP အချိန် - DEFAULT"))
        val lg = ChipGroup(this)
        lg.isSingleSelection = true
        lg.isSelectionRequired = true
        lengths.forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 12000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.saveInt(this, Prefs.KEY_MINUTES, minutesOf[i])
            }
            lg.addView(ch)
        }
        val curMin = Prefs.minutes(this)
        val li = minutesOf.indexOf(curMin)
        lg.check(if (li >= 0) 12000 + li else 12000)
        c4.addView(lg)
        c4.addView(sectionTitle("SEGMENTS"))
        val sg = ChipGroup(this)
        sg.isSingleSelection = true
        sg.isSelectionRequired = true
        listOf("Compact", "Full").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 12100 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.saveBool(this, Prefs.KEY_SEG_FULL, i == 1)
            }
            sg.addView(ch)
        }
        sg.check(if (Prefs.segFull(this)) 12101 else 12100)
        c4.addView(sg)
        root.addView(c4)

        val c5 = card()
        c5.addView(sectionTitle("ပြောဆိုမယ့်ဘာသာ - NARRATION"))
        val ng = ChipGroup(this)
        ng.isSingleSelection = true
        ng.isSelectionRequired = true
        langs.forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 13000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.save(this, Prefs.KEY_LANG, langOf[i])
            }
            ng.addView(ch)
        }
        val curLang = Prefs.lang(this)
        val ni = langOf.indexOf(curLang)
        ng.check(if (ni >= 0) 13000 + ni else 13000)
        c5.addView(ng)
        c5.addView(sectionTitle("VIDEO QUALITY"))
        val qg = ChipGroup(this)
        qg.isSingleSelection = true
        qg.isSelectionRequired = true
        listOf("720p", "1080p").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 13100 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.saveInt(this, Prefs.KEY_QUALITY, i)
            }
            qg.addView(ch)
        }
        qg.check(if (Prefs.quality(this) == 0) 13100 else 13101)
        c5.addView(qg)
        root.addView(c5)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == -1 && data?.data != null) {
            runCatching {
                val dst = File(filesDir, "wm_logo.jpg")
                contentResolver.openInputStream(data.data!!)?.use { ins ->
                    dst.outputStream().use { ins.copyTo(it) }
                }
                if (dst.length() > 0L) {
                    Prefs.save(this, Prefs.KEY_WM_LOGO, dst.absolutePath)
                    Toast.makeText(this, "Logo သိမ်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshUsage() {
        if (::usageText.isInitialized) usageText.text = Prefs.usageLine(this)
        if (::weekText.isInitialized) weekText.text = weekBars()
    }

    private fun weekBars(): String {
        val days = Prefs.usageWeek(this)
        var max = 1
        for ((_, a) in days) max = maxOf(max, a[0] + a[1] + a[2] + a[3])
        val sb = StringBuilder()
        for ((ds, a) in days) {
            val total = a[0] + a[1] + a[2] + a[3]
            val filled = total * 10 / max
            val label = if (ds.length >= 8) ds.substring(4, 6) + "-" + ds.substring(6, 8) else ds
            sb.append(label).append(" ")
            for (i in 0 until 10) sb.append(if (i < filled) "▓" else "░")
            sb.append(" ").append(total).append("  P").append(a[0]).append(" T").append(a[1]).append(" X").append(a[2]).append(" A").append(a[3]).append('\n')
        }
        return sb.toString().trim()
    }

    override fun onResume() {
        super.onResume()
        refreshUsage()
    }

    override fun onDestroy() {
        runCatching { testPlayer?.release() }
        testPlayer = null
        super.onDestroy()
    }
}


