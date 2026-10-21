package com.gikoko.recap

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

class HomeActivity : AppCompatActivity() {

    private val voices = listOf("Puck", "Charon", "Fenrir", "Orus", "Algenib", "Rasalgethi", "Kore", "Leda", "Aoede", "Despina", "Sulafet", "Zephyr")
    private val lengths = listOf("Auto", "1 min", "3 min", "5 min")
    private val minutesOf = intArrayOf(0, 1, 3, 5)

    private var mode = "youtube"
    private var pickedFile: File? = null
    private var captionsOn = true

    private lateinit var apiInput: EditText
    private lateinit var linkInput: EditText
    private lateinit var linkBox: LinearLayout
    private lateinit var fileBox: LinearLayout
    private lateinit var fileLabel: TextView
    private lateinit var voiceGroup: ChipGroup
    private lateinit var lengthGroup: ChipGroup
    private var ytBtn: Button? = null
    private var fileBtn: Button? = null

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onVideoPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        buildUi(root)
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
        c.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#2A2410"), Color.parseColor("#10182C"))
        )
        c.chipStrokeWidth = dp(1).toFloat()
        c.chipStrokeColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#FFC940"), Color.parseColor("#223055"))
        )
        c.setTextColor(ColorStateList(
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

    private fun buildUi(root: LinearLayout) {
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.img_splash_logo)
        logo.adjustViewBounds = true
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(48), dp(40), dp(48), dp(4))
        root.addView(logo, lp)

        val tag = TextView(this)
        tag.text = "ONE CLICK  \u2022  AI VIDEO RECAP"
        tag.gravity = Gravity.CENTER
        tag.setTextColor(Color.parseColor("#8A93B2"))
        tag.textSize = 12f
        tag.letterSpacing = 0.2f
        val tp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tp.setMargins(0, dp(4), 0, dp(12))
        root.addView(tag, tp)

        val c1 = card()
        c1.addView(sectionTitle("GEMINI API KEY"))
        apiInput = EditText(this)
        apiInput.hint = "aistudio.google.com/apikey"
        inputStyle(apiInput)
        val saved = Prefs.apiKey(this)
        if (saved.isNotEmpty()) apiInput.setText(saved)
        c1.addView(apiInput)
        val note1 = TextView(this)
        note1.text = "API key ကို app ထဲမှာဘဲ သိမ်းမယ် (Google က ယူပါ)"
        note1.setTextColor(Color.parseColor("#8A93B2"))
        note1.textSize = 11f
        note1.setPadding(0, dp(8), 0, 0)
        c1.addView(note1)
        root.addView(c1)

        val c2 = card()
        c2.addView(sectionTitle("VIDEO SOURCE"))
        val modes = LinearLayout(this)
        modes.orientation = LinearLayout.HORIZONTAL
        val ytB = modeButton("YouTube Link")
        val fbB = modeButton("Video File")
        ytBtn = ytB
        fileBtn = fbB
        val mp1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        mp1.rightMargin = dp(6)
        val mp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        mp2.leftMargin = dp(6)
        modes.addView(ytB, mp1)
        modes.addView(fbB, mp2)
        c2.addView(modes)

        linkBox = LinearLayout(this)
        linkBox.orientation = LinearLayout.VERTICAL
        linkInput = EditText(this)
        linkInput.hint = "https://youtube.com/watch?v=..."
        inputStyle(linkInput)
        linkBox.addView(linkInput)
        val note2 = TextView(this)
        note2.text = "Facebook / TikTok video တွေအတွက် Video File နဲ့ upload လုပ်ပါ"
        note2.setTextColor(Color.parseColor("#8A93B2"))
        note2.textSize = 11f
        note2.setPadding(0, dp(8), 0, 0)
        linkBox.addView(note2)
        val linkPad = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        linkPad.topMargin = dp(10)
        c2.addView(linkBox, linkPad)

        fileBox = LinearLayout(this)
        fileBox.orientation = LinearLayout.VERTICAL
        val pickBtn = Button(this)
        pickBtn.text = "ဖုန်းထဲက Video ရွေးမယ်"
        pickBtn.isAllCaps = false
        pickBtn.textSize = 13f
        pickBtn.setTextColor(Color.parseColor("#0A0A00"))
        pickBtn.setTypeface(null, Typeface.BOLD)
        pickBtn.setBackgroundResource(R.drawable.bg_gold_btn)
        pickBtn.setPadding(dp(8), dp(14), dp(8), dp(14))
        pickBtn.setOnClickListener {
            pickVideo.launch(arrayOf("video/*"))
        }
        fileBox.addView(pickBtn)
        fileLabel = TextView(this)
        fileLabel.text = "Video မရွေးရသေးပါ"
        fileLabel.setTextColor(Color.parseColor("#8A93B2"))
        fileLabel.textSize = 12f
        fileLabel.setPadding(dp(4), dp(8), dp(4), 0)
        fileBox.addView(fileLabel)
        val filePad = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        filePad.topMargin = dp(10)
        c2.addView(fileBox, filePad)
        root.addView(c2)

        ytB.setOnClickListener { setMode("youtube") }
        fbB.setOnClickListener { setMode("file") }
        setMode("youtube")

        val c3 = card()
        c3.addView(sectionTitle("RECAP \u2022 အချိန်"))
        lengthGroup = ChipGroup(this)
        lengthGroup.isSingleSelection = true
        lengthGroup.isSelectionRequired = true
        lengths.forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 1000 + i
            lengthGroup.addView(ch)
        }
        lengthGroup.check(1000)
        c3.addView(lengthGroup)
        root.addView(c3)

        val c4 = card()
        c4.addView(sectionTitle("AI \u2022 အသံမျိုးစုံ"))
        voiceGroup = ChipGroup(this)
        voiceGroup.isSingleSelection = true
        voiceGroup.isSelectionRequired = true
        voices.forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 2000 + i
            voiceGroup.addView(ch)
        }
        voiceGroup.check(2000)
        c4.addView(voiceGroup)
        root.addView(c4)

        val c5 = card()
        c5.addView(sectionTitle("CAPTIONS"))
        val sw = SwitchCompat(this)
        sw.text = "မြန်မာစာတန်းထိုး ထည့်မယ်"
        sw.textSize = 14f
        sw.setTextColor(Color.parseColor("#F2F5FF"))
        sw.isChecked = true
        sw.setOnCheckedChangeListener { _, checked -> captionsOn = checked }
        c5.addView(sw)
        root.addView(c5)

        val start = Button(this)
        start.text = "ONE CLICK RECAP"
        start.isAllCaps = false
        start.textSize = 16f
        start.setTypeface(null, Typeface.BOLD)
        start.setTextColor(Color.parseColor("#0A0A00"))
        start.setBackgroundResource(R.drawable.bg_start_btn)
        start.letterSpacing = 0.1f
        start.setPadding(0, dp(18), 0, dp(18))
        val sp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sp.setMargins(dp(16), dp(20), dp(16), dp(10))
        root.addView(start, sp)

        val pulseX = ObjectAnimator.ofFloat(start, "scaleX", 1f, 1.05f)
        val pulseY = ObjectAnimator.ofFloat(start, "scaleY", 1f, 1.05f)
        pulseX.repeatCount = ObjectAnimator.INFINITE
        pulseX.repeatMode = ObjectAnimator.REVERSE
        pulseY.repeatCount = ObjectAnimator.INFINITE
        pulseY.repeatMode = ObjectAnimator.REVERSE
        pulseX.duration = 700
        pulseY.duration = 700
        val beat = AnimatorSet()
        beat.playTogether(pulseX, pulseY)
        beat.start()

        val ver = TextView(this)
        ver.text = "GIkoko One Recap v1.0 \u2022 powered by Gemini"
        ver.gravity = Gravity.CENTER
        ver.setTextColor(Color.parseColor("#5A6480"))
        ver.textSize = 11f
        val vp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        vp.setMargins(0, dp(14), 0, dp(20))
        root.addView(ver, vp)

        start.setOnClickListener {
            val apiKey = apiInput.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Gemini API key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.saveApiKey(this, apiKey)
            val i = Intent(this, ProcessingActivity::class.java)
            i.putExtra("api_key", apiKey)
            i.putExtra("voice", selectedVoice())
            i.putExtra("minutes", selectedMinutes())
            i.putExtra("captions", captionsOn)
            if (mode == "youtube") {
                val url = linkInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, "YouTube link ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!GeminiClient.isYouTubeLink(url)) {
                    Toast.makeText(this, "Facebook/TikTok ဆိုရင် Video File နှိပ်ပြီး upload လုပ်ပါ", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                i.putExtra("source", "youtube")
                i.putExtra("url", url)
            } else {
                val f = pickedFile
                if (f == null || !f.exists()) {
                    Toast.makeText(this, "Video file ရွေးပေးပါ", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                i.putExtra("source", "file")
                i.putExtra("path", f.absolutePath)
            }
            startActivity(i)
        }
    }

    private fun modeButton(label: String): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 13f
        b.setTextColor(Color.parseColor("#C9D2EC"))
        b.background = null
        b.setPadding(dp(8), dp(14), dp(8), dp(14))
        return b
    }

    private fun setMode(m: String) {
        mode = m
        val yt = ytBtn
        val fb = fileBtn
        if (yt != null && fb != null) {
            if (m == "youtube") {
                yt.setBackgroundResource(R.drawable.bg_mode_card_selected)
                yt.setTextColor(Color.parseColor("#FFC940"))
                fb.setBackgroundResource(R.drawable.bg_mode_card)
                fb.setTextColor(Color.parseColor("#C9D2EC"))
                linkBox.visibility = View.VISIBLE
                fileBox.visibility = View.GONE
            } else {
                yt.setBackgroundResource(R.drawable.bg_mode_card)
                yt.setTextColor(Color.parseColor("#C9D2EC"))
                fb.setBackgroundResource(R.drawable.bg_mode_card_selected)
                fb.setTextColor(Color.parseColor("#FFC940"))
                linkBox.visibility = View.GONE
                fileBox.visibility = View.VISIBLE
            }
        }
    }

    private fun selectedVoice(): String {
        val id = voiceGroup.checkedChipId
        if (id == View.NO_ID) return "Puck"
        val c = voiceGroup.findViewById<Chip>(id)
        return c?.text?.toString() ?: "Puck"
    }

    private fun selectedMinutes(): Int {
        val id = lengthGroup.checkedChipId
        if (id == View.NO_ID) return 0
        val idx = id - 1000
        return if (idx in 0..3) minutesOf[idx] else 0
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

    private fun onVideoPicked(uri: Uri) {
        lifecycleScope.launch {
            try {
                fileLabel.text = "ကူးယူနေသည်..."
                val disp = queryName(uri)
                val ext = if (disp.lastIndexOf('.') > 0) disp.substring(disp.lastIndexOf('.')) else ".mp4"
                val dst = File(cacheDir, "picked_video" + ext)
                withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("ဖိုင်ဖွင့်မရပါ")
                    input.use { ins ->
                        dst.outputStream().use { ins.copyTo(it) }
                    }
                }
                if (dst.length() == 0L) throw IllegalStateException("ဖိုင်ဗလာဖြစ်နေသည်")
                pickedFile = dst
                val mb = dst.length() / (1024.0 * 1024.0)
                fileLabel.text = "ရွေးထားသည်: " + disp + " (" + String.format("%.1f", mb) + " MB)"
            } catch (e: Exception) {
                pickedFile = null
                fileLabel.text = "ကူးယူမအောင်မြင်: " + (e.message ?: "")
            }
        }
    }
}
