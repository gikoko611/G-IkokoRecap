package com.gikoko.recap

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.net.GeminiClient
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private val voices = listOf("Sulafat", "Vindemiatrix", "Erinome", "Aoede", "Kore", "Leda", "Zephyr", "Despina", "Charon", "Iapetus", "Achird", "Orus", "Puck", "Fenrir", "Algenib", "Rasalgethi")
    private val models = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.8-flash-lite")
    private val lengths = listOf("Auto", "1 min", "3 min", "5 min")
    private val minutesOf = intArrayOf(0, 1, 3, 5)
    private val langs = listOf("မြန်မာ", "English")
    private val langOf = listOf("my", "en")

    private lateinit var apiInput: EditText
    private lateinit var cacheText: TextView
    private lateinit var baseUrlInput: EditText
    private lateinit var customKeyInput: EditText
    private lateinit var customModelsGroup: ChipGroup
    private lateinit var modelStatus: TextView
    private lateinit var customCard: LinearLayout
    private val geminiModelCards = ArrayList<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
        refreshCacheSize()
        updateProviderVis()
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

    private fun updateProviderVis() {
        if (!::customCard.isInitialized) return
        val gemini = Prefs.provider(this) != "custom"
        for (c in geminiModelCards) c.visibility = if (gemini) View.VISIBLE else View.GONE
        customCard.visibility = if (gemini) View.GONE else View.VISIBLE
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

    private fun build(root: LinearLayout) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = android.view.Gravity.CENTER_VERTICAL
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
        ht.text = "SETTINGS"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val cP = card()
        cP.addView(sectionTitle("AI PROVIDER"))
        val pg = ChipGroup(this)
        pg.isSingleSelection = true
        pg.isSelectionRequired = true
        val pLabels = listOf("Gemini API", "Custom (REST API)")
        pLabels.forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 40000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    val v = if (i == 0) "gemini" else "custom"
                    Prefs.save(this, Prefs.KEY_PROVIDER, v)
                    updateProviderVis()
                }
            }
            pg.addView(ch)
        }
        pg.check(if (Prefs.provider(this) == "custom") 40001 else 40000)
        cP.addView(pg)
        val noteP = TextView(this)
        noteP.text = "Custom ကို OpenRouter၊ Groq စတဲ့ OpenAI-compatible REST provider တွေအတွက် သုံးပါ။"
        noteP.setTextColor(Color.parseColor("#8A93B2"))
        noteP.textSize = 11f
        cP.addView(noteP)
        root.addView(cP)

        val c1 = card()
        c1.addView(sectionTitle("GEMINI API KEY (Video + TTS အသံ)"))
        apiInput = EditText(this)
        apiInput.hint = "aistudio.google.com/apikey"
        inputStyle(apiInput)
        val saved = Prefs.apiKey(this)
        if (saved.isNotEmpty()) apiInput.setText(saved)
        c1.addView(apiInput)
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        val rp1 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rp1.topMargin = dp(10)
        c1.addView(row1, rp1)
        val saveBtn = flatBtn("SAVE", R.drawable.bg_gold_btn, "#0A0A00")
        val testBtn = flatBtn("TEST KEY", R.drawable.bg_chip_selected, "#F2F5FF")
        row1.addView(saveBtn, btnParams())
        row1.addView(testBtn, btnParams())
        saveBtn.setOnClickListener {
            val k = apiInput.text.toString().trim()
            if (k.isEmpty()) {
                Toast.makeText(this, "API key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
            } else {
                Prefs.saveApiKey(this, k)
                Toast.makeText(this, "သိမ်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
            }
        }
        testBtn.setOnClickListener {
            val k = apiInput.text.toString().trim()
            if (k.isEmpty()) {
                Toast.makeText(this, "API key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.saveApiKey(this, k)
            Toast.makeText(this, "စစ်ဆေးနေသည်...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val r = withContext(Dispatchers.IO) { GeminiClient.testKey(k) }
                    Toast.makeText(this@SettingsActivity, "Key မှန်ပါတယ် - " + r, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Key မမှန်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
        val note1 = TextView(this)
        note1.text = "အသံ (TTS) ထုတ်ဖို့ ဒီ key ကို သေချာထည့်ပါ။ Key မှားရင် 403 ဒဏ်းပေါ်မယ်။ VPN လိုနိုင်။"
        note1.setTextColor(Color.parseColor("#8A93B2"))
        note1.textSize = 11f
        note1.setPadding(0, dp(8), 0, 0)
        c1.addView(note1)
        root.addView(c1)

        val c2 = card()
        c2.addView(sectionTitle("AI MODEL (GEMINI)"))
        val mg = ChipGroup(this)
        mg.isSingleSelection = true
        mg.isSelectionRequired = true
        models.forEachIndexed { i, m ->
            val ch = chipOf(m)
            ch.id = 10000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.save(this, Prefs.KEY_MODEL, m)
            }
            mg.addView(ch)
        }
        val curModel = Prefs.model(this)
        val mi = models.indexOf(curModel)
        mg.check(if (mi >= 0) 10000 + mi else 10000)
        c2.addView(mg)
        val note2 = TextView(this)
        note2.text = "gemini-3.8-flash က အသစ်ဆုံးနဲ့ ပိုမြန်ပါ။"
        note2.setTextColor(Color.parseColor("#8A93B2"))
        note2.textSize = 11f
        c2.addView(note2)
        root.addView(c2)
        geminiModelCards.add(c2)

        val c3 = card()
        c3.addView(sectionTitle("အသံ - DEFAULT VOICE"))
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
        c3.addView(vg)
        val note3 = TextView(this)
        note3.text = "Sulafat (warm female) က မြန်မာ narration နဲ့ အနီးစပ်ဆုံး - default ပါ။ Vindemiatrix (gentle) ကလည်း သင့်တော်ပါတယ်။"
        note3.setTextColor(Color.parseColor("#8A93B2"))
        note3.textSize = 11f
        c3.addView(note3)
        root.addView(c3)

        customCard = card()
        customCard.addView(sectionTitle("CUSTOM PROVIDER (REST API)"))
        baseUrlInput = EditText(this)
        baseUrlInput.hint = "https://openrouter.ai/api/v1"
        inputStyle(baseUrlInput)
        val savedBase = Prefs.baseUrl(this)
        if (savedBase.isNotEmpty()) baseUrlInput.setText(savedBase)
        customCard.addView(baseUrlInput)
        customKeyInput = EditText(this)
        customKeyInput.hint = "Provider API key (openrouter.ai/keys)"
        inputStyle(customKeyInput)
        val savedCk = Prefs.customKey(this)
        if (savedCk.isNotEmpty()) customKeyInput.setText(savedCk)
        val cpad = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cpad.topMargin = dp(10)
        customCard.addView(customKeyInput, cpad)
        val rowC = LinearLayout(this)
        rowC.orientation = LinearLayout.HORIZONTAL
        val rpC = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rpC.topMargin = dp(10)
        customCard.addView(rowC, rpC)
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
                    Toast.makeText(this@SettingsActivity, "Key မှန်ပါတယ် - " + r, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "မမှန်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }

        customCard.addView(sectionTitle("FREE MODELS"))
        modelStatus = TextView(this)
        modelStatus.text = "REFRESH နှိပ်ပြီး provider ရဲ့ free model တွေ ရယူပါ"
        modelStatus.setTextColor(Color.parseColor("#8A93B2"))
        modelStatus.textSize = 11f
        customCard.addView(modelStatus)
        customModelsGroup = ChipGroup(this)
        customModelsGroup.isSingleSelection = true
        customModelsGroup.isSelectionRequired = true
        customCard.addView(customModelsGroup)
        val refreshC = flatBtn("REFRESH - FREE MODELS", R.drawable.bg_mode_card, "#C9D2EC")
        val rpR = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rpR.topMargin = dp(10)
        customCard.addView(refreshC, rpR)
        refreshC.setOnClickListener {
            val b = baseUrlInput.text.toString().trim()
            val k = customKeyInput.text.toString().trim()
            if (b.isEmpty() || k.isEmpty()) {
                Toast.makeText(this, "Base URL နဲ့ Key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
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
        root.addView(customCard)

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

        val c6 = card()
        c6.addView(sectionTitle("EXTRA FEATURES"))
        val swWm = switchRow("Watermark ထည့်မည်", Prefs.KEY_WATERMARK, true)
        val swIo = switchRow("Intro / Outro ထည့်မည်", Prefs.KEY_INTRO, true)
        val swAuto = switchRow("ပြီးရင် Download ထဲ auto-save", Prefs.KEY_AUTOSAVE, false)
        val swHap = switchRow("Haptic (တုန်ခါမှု) ဖွင့်မည်", Prefs.KEY_HAPTIC, true)
        c6.addView(swWm)
        val sp6 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sp6.topMargin = dp(10)
        c6.addView(swIo, sp6)
        c6.addView(swAuto, sp6)
        c6.addView(swHap, sp6)
        root.addView(c6)

        val c7 = card()
        c7.addView(sectionTitle("CACHE"))
        cacheText = TextView(this)
        cacheText.setTextColor(Color.parseColor("#C9D2EC"))
        cacheText.textSize = 13f
        c7.addView(cacheText)
        val clearBtn = flatBtn("CACHE ရှင်းမည်", R.drawable.bg_mode_card, "#C9D2EC")
        val cp7 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cp7.topMargin = dp(10)
        c7.addView(clearBtn, cp7)
        clearBtn.setOnClickListener {
            deleteRecursive(cacheDir)
            refreshCacheSize()
            Toast.makeText(this, "Cache ရှင်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
        }
        root.addView(c7)

        val c8 = card()
        c8.addView(sectionTitle("ABOUT"))
        val ab1 = TextView(this)
        ab1.text = "GIkoko One Recap v2.1.1 (Pro)"
        ab1.setTextColor(Color.parseColor("#FFC940"))
        ab1.textSize = 15f
        ab1.setTypeface(null, Typeface.BOLD)
        c8.addView(ab1)
        val ab2 = TextView(this)
        ab2.text = "Gemini 3.8 Flash + Custom REST provider + Free models. Developed by I Ko Ko"
        ab2.setTextColor(Color.parseColor("#8A93B2"))
        ab2.textSize = 12f
        ab2.setPadding(0, dp(6), 0, 0)
        c8.addView(ab2)
        root.addView(c8)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
    }

    private fun refreshCacheSize() {
        val bytes = sizeOf(cacheDir)
        val mb = bytes / (1024.0 * 1024.0)
        cacheText.text = "Cache အရွယ်အစား: " + String.format("%.1f", mb) + " MB"
    }

    private fun sizeOf(f: File): Long {
        var s = 0L
        if (f.isDirectory) {
            val kids = f.listFiles()
            if (kids != null) for (k in kids) s += sizeOf(k)
        } else {
            s = f.length()
        }
        return s
    }

    private fun deleteRecursive(f: File) {
        if (f.isDirectory) {
            val kids = f.listFiles()
            if (kids != null) for (k in kids) deleteRecursive(k)
        }
        f.delete()
    }
}
