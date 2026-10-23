package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class TextRecapActivity : AppCompatActivity() {

    private lateinit var textInput: EditText
    private lateinit var countText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open text recap")
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
        ht.text = "TEXT TO RECAP"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c0 = card()
        val n0 = note("ဆောင်းပါး / စာအုပ်စာပိုဒ် / ဘလော့ post တွေကို paste လုပ်ရင် AI က ခြုံငုံပြီး themed recap video (TTS narration + SRT) အဖြစ် ပြောင်းပေးမယ် - video upload မလိုလို့ quota မစားပါ။")
        c0.addView(n0)
        root.addView(c0)

        val c1 = card()
        c1.addView(sectionTitle("📄 စာသား ထည့်ပါ"))
        textInput = EditText(this)
        textInput.hint = "ဒီမှာ စာသားကို paste လုပ်ပါ..."
        textInput.setBackgroundResource(R.drawable.bg_input)
        textInput.setTextColor(Color.parseColor("#F2F5FF"))
        textInput.setHintTextColor(Color.parseColor("#5A6480"))
        textInput.textSize = 13f
        textInput.setSingleLine(false)
        textInput.minLines = 10
        textInput.gravity = Gravity.TOP
        textInput.setPadding(dp(12), dp(12), dp(12), dp(12))
        c1.addView(textInput)
        countText = note("စာလုံး: 0")
        countText.setPadding(0, dp(8), 0, 0)
        c1.addView(countText)
        textInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                countText.text = "စာလုံး: " + (s?.toString()?.trim()?.length ?: 0)
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })
        root.addView(c1)

        val c2 = card()
        val runBtn = flatBtn("🎬 TEXT RECAP လုပ်မည်", R.drawable.bg_gold_btn, "#0A0A00")
        c2.addView(runBtn)
        runBtn.setOnClickListener { run() }
        val n2 = note("TRANSCRIPT MODE provider (TOOLS ထဲမှာရှိ) ကို သုံးမှာဆိုင်း - free provider ရယူပြီး KEY ထည့်ထားပါ။ Theme / watermark / voice တွေက TOOLS ရွေးထားတဲ့အတိုင်း အလိုအလျောက် သုံးပါမယ်။")
        n2.setPadding(0, dp(8), 0, 0)
        c2.addView(n2)
        root.addView(c2)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
    }

    private fun run() {
        val txt = textInput.text.toString().trim()
        if (txt.length < 40) {
            Toast.makeText(this, "အနည်းဆုံး စာလုံး 40 ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        if (Prefs.extKey(this).isEmpty()) {
            val d = AlertDialog.Builder(this)
                .setTitle("TRANSCRIPT MODE လိုပါတယ်")
                .setMessage("TEXT RECAP မှာ TRANSCRIPT MODE provider KEY လိုပါတယ်။ TOOLS → TRANSCRIPT MODE မှာ OpenRouter / Groq / ModelScope (free) သို့မဟုတ် CUSTOM LINK နဲ့ provider ထည့်ပြီး KEY သိမ်းပါ။")
                .setPositiveButton("TOOLS သွားမည်") { _, _ ->
                    startActivity(Intent(this, ToolsActivity::class.java))
                }
                .setNegativeButton("နောက်မှ") { dd, _ -> dd.dismiss() }
                .show()
            return
        }
        val f = File(cacheDir, "text_input.txt")
        f.writeText(txt)
        val i = Intent(this, ProcessingActivity::class.java)
        i.putExtra("source", "text")
        i.putExtra("text_path", f.absolutePath)
        startActivity(i)
    }
}
