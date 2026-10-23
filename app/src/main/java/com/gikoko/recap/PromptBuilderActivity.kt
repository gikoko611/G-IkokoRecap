package com.gikoko.recap

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class PromptBuilderActivity : AppCompatActivity() {

    private val styles = listOf("cinematic", "anime", "cyberpunk", "3D render", "photorealistic", "watercolor", "comic book", "pixel art")
    private val cameras = listOf("wide shot", "close-up", "aerial drone shot", "POV shot", "low angle", "macro shot")
    private val lights = listOf("golden hour", "neon lighting", "soft studio light", "moonlight", "volumetric light", "dramatic shadows")
    private val moods = listOf("epic", "calm and peaceful", "mysterious", "romantic", "action-packed", "nostalgic")
    private val formats = listOf("16:9 widescreen composition", "9:16 vertical composition", "1:1 square composition")

    private val chosen = HashSet<String>()
    private lateinit var subjectInput: EditText
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open prompt builder")
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
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

    private fun sectionTitle(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 13f
        tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD), Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#3DE8F8"))
        tv.letterSpacing = 0.15f
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.bottomMargin = dp(8)
        tv.layoutParams = p
        return tv
    }

    private fun note(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#8A93B2"))
        tv.textSize = 11f
        return tv
    }

    private fun chip(label: String): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 12f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.parseColor("#A7B6DC"))
        tv.setBackgroundResource(R.drawable.bg_input)
        tv.setPadding(dp(8), dp(9), dp(8), dp(9))
        tv.setOnClickListener {
            if (chosen.contains(label)) {
                chosen.remove(label)
                tv.setTextColor(Color.parseColor("#A7B6DC"))
                tv.setBackgroundResource(R.drawable.bg_input)
            } else {
                chosen.add(label)
                tv.setTextColor(Color.parseColor("#0A0A00"))
                tv.setTypeface(null, Typeface.BOLD)
                tv.setBackgroundResource(R.drawable.bg_gold_btn)
            }
        }
        return tv
    }

    private fun chipGroup(options: List<String>): LinearLayout {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        var i = 0
        while (i < options.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            wrap.addView(row, rp)
            for (j in 0 until 2) {
                if (i + j < options.size) {
                    val c = chip(options[i + j])
                    val cp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    cp.setMargins(dp(3), dp(3), dp(3), dp(3))
                    row.addView(c, cp)
                }
            }
            i += 2
        }
        return wrap
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
        ht.text = "PROMPT BUILDER PRO"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c1 = card()
        c1.addView(sectionTitle("✏️ SUBJECT - ဘာအကြောင်းလဲ"))
        subjectInput = EditText(this)
        subjectInput.hint = "ဥပမာ: golden pagoda at sunrise"
        subjectInput.textSize = 13f
        subjectInput.setTextColor(Color.parseColor("#F2F5FF"))
        subjectInput.setHintTextColor(Color.parseColor("#8A93B2"))
        subjectInput.setBackgroundResource(R.drawable.bg_input)
        subjectInput.setPadding(dp(10), dp(10), dp(10), dp(10))
        c1.addView(subjectInput)
        val note1 = note("Subject ကို အင်္ဂလိပ်လို ရိုက်ပါ (AI တွေက အင်္ဂလိပ်မှာ အကောင်းဆုံးနားလည်ပါတယ်)။ မရိုက်ချင်ရင် PROMPT IDEAS ထဲက prompt တစ်ခုကို ဒီမှာ paste လုပ်ပြီး အောက်က chips တွေ ထပ်ရွေးလို့ရပါတယ်။")
        note1.setPadding(0, dp(8), 0, 0)
        c1.addView(note1)
        root.addView(c1)

        val c2 = card()
        c2.addView(sectionTitle("🎨 STYLE"))
        c2.addView(chipGroup(styles))
        val camT = TextView(this)
        camT.text = "📹 CAMERA"
        camT.textSize = 13f
        camT.setTextColor(Color.parseColor("#3DE8F8"))
        camT.letterSpacing = 0.15f
        val camP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        camP.topMargin = dp(12)
        camP.bottomMargin = dp(8)
        c2.addView(camT, camP)
        c2.addView(chipGroup(cameras))
        val ligT = TextView(this)
        ligT.text = "💡 LIGHTING"
        ligT.textSize = 13f
        ligT.setTextColor(Color.parseColor("#3DE8F8"))
        ligT.letterSpacing = 0.15f
        val ligP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        ligP.topMargin = dp(12)
        ligP.bottomMargin = dp(8)
        c2.addView(ligT, ligP)
        c2.addView(chipGroup(lights))
        val moodT = TextView(this)
        moodT.text = "🎭 MOOD"
        moodT.textSize = 13f
        moodT.setTextColor(Color.parseColor("#3DE8F8"))
        moodT.letterSpacing = 0.15f
        val moodP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        moodP.topMargin = dp(12)
        moodP.bottomMargin = dp(8)
        c2.addView(moodT, moodP)
        c2.addView(chipGroup(moods))
        val fmtT = TextView(this)
        fmtT.text = "📐 FORMAT"
        fmtT.textSize = 13f
        fmtT.setTextColor(Color.parseColor("#3DE8F8"))
        fmtT.letterSpacing = 0.15f
        val fmtP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        fmtP.topMargin = dp(12)
        fmtP.bottomMargin = dp(8)
        c2.addView(fmtT, fmtP)
        c2.addView(chipGroup(formats))
        val note2 = note("Chips တွေ မရွေးရင်လည်း ရပါတယ် - ရွေးရုံ၊ subject နဲ့ပဲ အလိုအလျောက် pro prompt ဆွဲပေးမယ်။")
        note2.setPadding(0, dp(8), 0, 0)
        c2.addView(note2)
        root.addView(c2)

        val c3 = card()
        c3.addView(sectionTitle("🛠 RESULT"))
        val buildBtn = Button(this)
        buildBtn.text = "⚡ PROMPT ဆွဲမယ်"
        buildBtn.isAllCaps = false
        buildBtn.textSize = 14f
        buildBtn.setTypeface(null, Typeface.BOLD)
        buildBtn.setTextColor(Color.parseColor("#0A0A00"))
        buildBtn.setBackgroundResource(R.drawable.bg_gold_btn)
        buildBtn.setPadding(0, dp(12), 0, dp(12))
        c3.addView(buildBtn)
        buildBtn.setOnClickListener { buildResult() }

        resultText = TextView(this)
        resultText.setTextColor(Color.parseColor("#F2F5FF"))
        resultText.textSize = 12f
        resultText.typeface = android.graphics.Typeface.MONOSPACE
        resultText.setTextIsSelectable(true)
        resultText.setBackgroundResource(R.drawable.bg_input)
        resultText.setPadding(dp(12), dp(12), dp(12), dp(12))
        val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rp.topMargin = dp(10)
        c3.addView(resultText, rp)

        val copyBtn = Button(this)
        copyBtn.text = "📋 COPY - zsky input မှာ Paste လုပ်ပါ"
        copyBtn.isAllCaps = false
        copyBtn.textSize = 13f
        copyBtn.setTextColor(Color.parseColor("#3DE8F8"))
        copyBtn.setBackgroundResource(R.drawable.bg_chip_selected)
        copyBtn.setPadding(0, dp(10), 0, dp(10))
        val cbp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cbp.topMargin = dp(8)
        c3.addView(copyBtn, cbp)
        copyBtn.setOnClickListener { copyCurrent() }

        val varBtn = Button(this)
        varBtn.text = "🔄 VARIATIONS - prompt ၄မျိုး ထုတ်မယ်"
        varBtn.isAllCaps = false
        varBtn.textSize = 13f
        varBtn.setTextColor(Color.parseColor("#F2F5FF"))
        varBtn.setBackgroundResource(R.drawable.bg_chip_selected)
        varBtn.setPadding(0, dp(10), 0, dp(10))
        val vbp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        vbp.topMargin = dp(6)
        c3.addView(varBtn, vbp)
        varBtn.setOnClickListener { showVariations() }
        root.addView(c3)

        val warn = note("💡 Pro tip: subject + style + camera + lighting + mood + quality words — ဒါက pro တွေရေးတဲ့ prompt ဖွဲ့စည်းပုံပါ။ Negative prompt ကလည်း အလိုအလျောက် ပါလာမယ် (blurry/low quality တာတွေ ကာကွယ်ဖို့)။")
        warn.setPadding(dp(16), dp(8), dp(16), dp(16))
        root.addView(warn)
    }

    private fun subject(): String {
        val s = subjectInput.text.toString().trim()
        return if (s.isEmpty()) "beautiful cinematic scene" else s
    }

    private fun buildPrompt(subj: String): String {
        val parts = ArrayList<String>()
        parts.add(subj)
        val st = styles.filter { chosen.contains(it) }
        if (st.isNotEmpty()) parts.add(st.joinToString(", "))
        val cm = cameras.filter { chosen.contains(it) }
        if (cm.isNotEmpty()) parts.add(cm.joinToString(", "))
        val li = lights.filter { chosen.contains(it) }
        if (li.isNotEmpty()) parts.add(li.joinToString(", "))
        val md = moods.filter { chosen.contains(it) }
        if (md.isNotEmpty()) parts.add(md.joinToString(", "))
        val fm = formats.filter { chosen.contains(it) }
        if (fm.isNotEmpty()) parts.add(fm.joinToString(", "))
        parts.add("ultra detailed, 8K, sharp focus, high quality")
        return parts.joinToString(", ")
    }

    private fun negative(): String {
        return "Negative prompt: blurry, low quality, distorted faces, watermark, text artifacts, extra limbs, bad anatomy"
    }

    private fun buildResult() {
        val p = buildPrompt(subject())
        resultText.text = p + "\n\n" + negative()
    }

    private fun currentFull(): String {
        val t = resultText.text.toString()
        return if (t.isEmpty()) buildPrompt(subject()) + "\n\n" + negative() else t
    }

    private fun copyCurrent() {
        val txt = currentFull()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("prompt", txt))
        Toast.makeText(this, "Prompt ကူးယူပြီး ✓ - AI STUDIO input မှာ Paste လုပ်ပါ", Toast.LENGTH_LONG).show()
    }

    private fun showVariations() {
        val subj = subject()
        val vs = Array(4) { i ->
            val sb = StringBuilder(subj)
            sb.append(", ").append(styles[(i + 1) % styles.size])
            sb.append(", ").append(cameras[(i * 2 + 1) % cameras.size])
            sb.append(", ").append(lights[(i * 3 + 2) % lights.size])
            sb.append(", ").append(moods[(i + 3) % moods.size])
            sb.append(", ultra detailed, 8K, sharp focus, high quality")
            sb.toString()
        }
        val labels = vs.mapIndexed { i, s -> (i + 1).toString() + ". " + s }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("🔄 VARIATIONS - နှိပ်ရင် ကူးယူမယ်")
            .setItems(labels) { _, which ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("prompt", vs[which] + "\n\n" + negative()))
                Toast.makeText(this, "Variation " + (which + 1) + " ကူးယူပြီး ✓ - Paste လုပ်ပါ", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("ပိတ်", null)
            .show()
    }
}
