package com.gikoko.recap

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.recap.WavUtil
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
    private var segsFull = false
    private var previewPlayer: MediaPlayer? = null

    private lateinit var drawer: DrawerLayout
    private lateinit var linkInput: EditText
    private lateinit var linkBox: LinearLayout
    private lateinit var fileBox: LinearLayout
    private lateinit var fileLabel: TextView
    private lateinit var voiceGroup: ChipGroup
    private lateinit var lengthGroup: ChipGroup
    private lateinit var statusCard: LinearLayout
    private var ytBtn: Button? = null
    private var fileBtn: Button? = null

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onVideoPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        drawer = findViewById(R.id.drawerLayout)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        val panel = findViewById<LinearLayout>(R.id.drawerPanel)
        findViewById<TextView>(R.id.drawerBtn).setOnClickListener {
            drawer.openDrawer(Gravity.START)
        }
        buildDrawer(panel)
        buildUi(root)
    }

    override fun onResume() {
        super.onResume()
        if (::statusCard.isInitialized) refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.release()
        previewPlayer = null
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

    private fun menuItem(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 15f
        tv.setTextColor(Color.parseColor("#C9D2EC"))
        tv.setPadding(dp(12), dp(16), dp(12), dp(16))
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun buildDrawer(panel: LinearLayout) {
        val head = TextView(this)
        head.text = "GIKOKO"
        head.textSize = 22f
        head.setTypeface(null, Typeface.BOLD)
        head.setTextColor(Color.parseColor("#FFC940"))
        head.letterSpacing = 0.2f
        head.setPadding(dp(12), dp(24), dp(12), dp(2))
        panel.addView(head)

        val sub = TextView(this)
        sub.text = "ONE RECAP PRO"
        sub.textSize = 12f
        sub.setTextColor(Color.parseColor("#3DE8F8"))
        sub.letterSpacing = 0.25f
        sub.setPadding(dp(12), 0, dp(12), dp(16))
        panel.addView(sub)

        val divider = View(this)
        divider.setBackgroundColor(Color.parseColor("#223055"))
        val dp1 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        panel.addView(divider, dp1)

        panel.addView(menuItem("🏠  Home") { drawer.closeDrawer(Gravity.START) })
        panel.addView(menuItem("⚙️  Settings") {
            drawer.closeDrawer(Gravity.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        panel.addView(menuItem("🕘  History") {
            drawer.closeDrawer(Gravity.START)
            startActivity(Intent(this, HistoryActivity::class.java))
        })
        panel.addView(menuItem("ℹ️  About") {
            drawer.closeDrawer(Gravity.START)
            AlertDialog.Builder(this)
                .setTitle("GIkoko One Recap")
                .setMessage("Version 2.0 (Pro)\n\nAI video recap - Myanmar voice + captions.\nPowered by Gemini. Developed by I Ko Ko.")
                .setPositiveButton("OK", null)
                .show()
        })

        val foot = TextView(this)
        foot.text = "v2.0 Pro"
        foot.textSize = 11f
        foot.setTextColor(Color.parseColor("#5A6480"))
        val fp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        fp.topMargin = dp(40)
        panel.addView(foot, fp)
    }

    private fun refreshStatus() {
        statusCard.removeAllViews()
        val key = Prefs.apiKey(this)
        if (key.isEmpty()) {
            val warn = TextView(this)
            warn.text = "API key မထည့်ရသေးပါ"
            warn.setTextColor(Color.parseColor("#FFC940"))
            warn.textSize = 15f
            warn.setTypeface(null, Typeface.BOLD)
            statusCard.addView(warn)

            val note = TextView(this)
            note.text = "Recap လုပ်ဖို့ Gemini API key လိုပါတယ်။ Settings မှာ ထည့်ပေးပါ။"
            note.setTextColor(Color.parseColor("#8A93B2"))
            note.textSize = 12f
            note.setPadding(0, dp(6), 0, dp(10))
            statusCard.addView(note)

            val go = Button(this)
            go.text = "SETTINGS မှာ ထည့်မည်"
            go.isAllCaps = false
            go.textSize = 13f
            go.setTextColor(Color.parseColor("#0A0A00"))
            go.setTypeface(null, Typeface.BOLD)
            go.setBackgroundResource(R.drawable.bg_gold_btn)
            go.setPadding(dp(8), dp(12), dp(8), dp(12))
            go.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
            statusCard.addView(go)
        } else {
            val ok = TextView(this)
            ok.text = "API KEY ✓ သင့်နေပါပြီ"
            ok.setTextColor(Color.parseColor("#3DE8F8"))
            ok.textSize = 15f
            ok.setTypeface(null, Typeface.BOLD)
            statusCard.addView(ok)

            val info = TextView(this)
            info.text = "Model: " + Prefs.model(this) + "  \u2022  Voice: " + Prefs.voice(this) + "  \u2022  ပြောင်းချင်ရင် နှိပ်ပါ"
            info.setTextColor(Color.parseColor("#8A93B2"))
            info.textSize = 11f
            info.setPadding(0, dp(6), 0, 0)
            statusCard.addView(info)

            statusCard.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        }
    }

    private fun buildUi(root: LinearLayout) {
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.img_splash_logo)
        logo.adjustViewBounds = true
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(48), dp(16), dp(48), dp(4))
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

        statusCard = card()
        refreshStatus()
        root.addView(statusCard)

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
        val curMin = Prefs.minutes(this)
        val li = minutesOf.indexOf(curMin)
        lengthGroup.check(if (li >= 0) 1000 + li else 1000)
        c3.addView(lengthGroup)

        c3.addView(sectionTitle("SEGMENTS"))
        val sg = ChipGroup(this)
        sg.isSingleSelection = true
        sg.isSelectionRequired = true
        listOf("Compact", "Full").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 3100 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    segsFull = i == 1
                    Prefs.saveBool(this, Prefs.KEY_SEG_FULL, segsFull)
                }
            }
            sg.addView(ch)
        }
        segsFull = Prefs.segFull(this)
        sg.check(if (segsFull) 3101 else 3100)
        c3.addView(sg)
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
        val curVoice = Prefs.voice(this)
        val vi = voices.indexOf(curVoice)
        voiceGroup.check(if (vi >= 0) 2000 + vi else 2000)
        c4.addView(voiceGroup)

        val previewBtn = Button(this)
        previewBtn.text = "🔊 အသံစမ်းကြည့်မည် (PREVIEW)"
        previewBtn.isAllCaps = false
        previewBtn.textSize = 13f
        previewBtn.setTextColor(Color.parseColor("#F2F5FF"))
        previewBtn.setBackgroundResource(R.drawable.bg_chip_selected)
        previewBtn.setPadding(dp(8), dp(12), dp(8), dp(12))
        val pp4 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pp4.topMargin = dp(10)
        c4.addView(previewBtn, pp4)
        previewBtn.setOnClickListener { previewVoice() }
        root.addView(c4)

        val c5 = card()
        c5.addView(sectionTitle("CAPTIONS"))
        val sw = SwitchCompat(this)
        sw.text = "မြန်မာစာတန်းထိုး ထည့်မယ်"
        sw.textSize = 14f
        sw.setTextColor(Color.parseColor("#F2F5FF"))
        captionsOn = Prefs.captions(this)
        sw.isChecked = captionsOn
        sw.setOnCheckedChangeListener { _, checked ->
            captionsOn = checked
            Prefs.saveBool(this, Prefs.KEY_CAPTIONS, checked)
        }
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
        ver.text = "GIkoko One Recap v2.0 Pro \u2022 powered by Gemini"
        ver.gravity = Gravity.CENTER
        ver.setTextColor(Color.parseColor("#5A6480"))
        ver.textSize = 11f
        val vp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        vp.setMargins(0, dp(14), 0, dp(20))
        root.addView(ver, vp)

        start.setOnClickListener {
            val key = Prefs.apiKey(this)
            if (key.isEmpty()) {
                Toast.makeText(this, "Settings မှာ Gemini API key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SettingsActivity::class.java))
                return@setOnClickListener
            }
            if (Prefs.haptic(this)) {
                start.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
            Prefs.save(this, Prefs.KEY_VOICE, selectedVoice())
            Prefs.saveInt(this, Prefs.KEY_MINUTES, selectedMinutes())
            val i = Intent(this, ProcessingActivity::class.java)
            i.putExtra("api_key", key)
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

    private fun previewVoice() {
        val key = Prefs.apiKey(this)
        if (key.isEmpty()) {
            Toast.makeText(this, "Settings မှာ API key ထည့်ပေးပါ", Toast.LENGTH_LONG).show()
            return
        }
        val v = selectedVoice()
        Toast.makeText(this, "အသံထုတ်နေသည်...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val (pcm, rate) = withContext(Dispatchers.IO) {
                    GeminiClient.generateTts(key, "GIkoko One Recap မှ ကြိုဆိုပါတယ်။ ဒီက သင်ရွေးထားတဲ့ အသံပါ။", v)
                }
                val wav = WavUtil.pcmToWav(pcm, rate)
                val f = File(cacheDir, "voice_preview.wav")
                withContext(Dispatchers.IO) { f.writeBytes(wav) }
                previewPlayer?.release()
                previewPlayer = MediaPlayer()
                previewPlayer?.setDataSource(f.absolutePath)
                previewPlayer?.prepare()
                previewPlayer?.start()
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "အသံစမ်းမအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
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
        if (id == View.NO_ID) return Prefs.voice(this)
        val c = voiceGroup.findViewById<Chip>(id)
        return c?.text?.toString() ?: Prefs.voice(this)
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
