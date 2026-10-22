package com.gikoko.recap

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.net.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceLabActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recording = false
    private var matchedVoice = ""
    private var matchedStyle = ""
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private val sampleFile: File get() = File(filesDir, "voice_sample.m4a")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#05070E"))
        scroll.setFillViewport(true)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
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

    private fun note(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#8A93B2"))
        tv.textSize = 11f
        tv.setPadding(0, dp(8), 0, 0)
        return tv
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
        ht.text = "VOICE LAB"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c1 = card()
        c1.addView(sectionTitle(L.s(this, "VOICE CLONE - ကိုယ့်အသံပုံစံ", "VOICE CLONE - YOUR VOICE")))
        c1.addView(note(L.s(this, "မိမိအသံကို စက္ကန့် ၃၀ ခန့် ဖမ်းပါ။ AI က Gemini အသံ ၁၆ မျိုးထဲမှ အနီးစပ်ဆုံး အသံနဲ့ ပြောပုံစံ ရွေးပေးပါမယ်။ (ရုပ်တူ ၁:၁ clone တော့ မဟုတ်ပါ - smart match ပါ။)", "Record ~30s of your voice. AI picks the closest of 16 prebuilt voices + a style. (Smart match, not a true 1:1 clone.)")))

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        val recBtn = flatBtn(L.s(this, "အသံဖမ်းမည် - RECORD", "RECORD"), R.drawable.bg_gold_btn, "#0A0A00")
        val stopBtn = flatBtn(L.s(this, "ရပ်မည် - STOP", "STOP"), R.drawable.bg_chip_selected, "#F2F5FF")
        row1.addView(recBtn, btnParams())
        row1.addView(stopBtn, btnParams())
        val r1P = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        r1P.topMargin = dp(10)
        c1.addView(row1, r1P)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val playBtn = flatBtn(L.s(this, "နားထောင်မည် - PLAY", "PLAY"), R.drawable.bg_mode_card, "#C9D2EC")
        val matchBtn = flatBtn(L.s(this, "AI MATCH", "AI MATCH"), R.drawable.bg_gold_btn, "#0A0A00")
        row2.addView(playBtn, btnParams())
        row2.addView(matchBtn, btnParams())
        val r2P = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        r2P.topMargin = dp(10)
        c1.addView(row2, r2P)

        statusText = TextView(this)
        statusText.setTextColor(Color.parseColor("#C9D2EC"))
        statusText.textSize = 12f
        val stP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        stP.topMargin = dp(10)
        c1.addView(statusText, stP)

        resultText = TextView(this)
        resultText.setTextColor(Color.parseColor("#FFC940"))
        resultText.textSize = 13f
        val rtP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rtP.topMargin = dp(6)
        c1.addView(resultText, rtP)

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        val useBtn = flatBtn(L.s(this, "ဒီအသံနဲ့ သုံးမည်", "USE THIS VOICE"), R.drawable.bg_chip_selected, "#F2F5FF")
        val resetBtn = flatBtn(L.s(this, "RESET (Sulafat)", "RESET (Sulafat)"), R.drawable.bg_mode_card, "#C9D2EC")
        row3.addView(useBtn, btnParams())
        row3.addView(resetBtn, btnParams())
        val r3P = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        r3P.topMargin = dp(10)
        c1.addView(row3, r3P)
        root.addView(c1)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)

        recBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 90)
                return@setOnClickListener
            }
            startRecording()
        }
        stopBtn.setOnClickListener { stopRecording() }
        playBtn.setOnClickListener { playSample() }
        matchBtn.setOnClickListener { aiMatch() }
        useBtn.setOnClickListener { useVoice() }
        resetBtn.setOnClickListener { resetVoice() }
        refreshResult()
    }

    @Suppress("DEPRECATION")
    private fun startRecording() {
        if (recording) return
        try {
            sampleFile.delete()
            val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(sampleFile.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recording = true
            statusText.text = L.s(this, "ဖမ်းနေသည် ● - ပြီးရင် STOP နှိပ်ပါ", "Recording ● - tap STOP when done")
        } catch (e: Exception) {
            statusText.text = "REC error: " + (e.message ?: "")
            runCatching { recorder?.release() }
            recorder = null
            recording = false
        }
    }

    private fun stopRecording() {
        if (!recording) return
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        if (sampleFile.exists()) {
            statusText.text = L.s(this, "သိမ်းပြီးပါပြီ (" + String.format("%.1f", sampleFile.length() / 1024.0) + " KB) ✓", "Saved (" + String.format("%.1f", sampleFile.length() / 1024.0) + " KB) ✓")
        } else {
            statusText.text = L.s(this, "အသံ မရပါ", "No recording")
        }
    }

    private fun playSample() {
        if (!sampleFile.exists() || sampleFile.length() < 100L) {
            Toast.makeText(this, L.s(this, "အရင် အသံဖမ်းပါ (RECORD)", "Record your voice first"), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            runCatching { player?.release() }
            player = MediaPlayer()
            player!!.setDataSource(sampleFile.absolutePath)
            player!!.setOnCompletionListener { mp ->
                mp.release()
                player = null
            }
            player!!.prepare()
            player!!.start()
            statusText.text = L.s(this, "နားထောင်နေသည် ▶", "Playing ▶")
        } catch (e: Exception) {
            statusText.text = "Play error: " + (e.message ?: "")
        }
    }

    private fun aiMatch() {
        if (Prefs.apiKey(this).isEmpty()) {
            Toast.makeText(this, L.s(this, "Gemini API key လိုပါတယ် - Settings မှာ ထည့်ပါ", "Gemini API key needed - add it in Settings"), Toast.LENGTH_LONG).show()
            return
        }
        if (!sampleFile.exists() || sampleFile.length() < 2000L) {
            Toast.makeText(this, L.s(this, "အရင် အသံဖမ်းပါ (RECORD)", "Record your voice first"), Toast.LENGTH_LONG).show()
            return
        }
        statusText.text = L.s(this, "AI နဲ့ အသံမတ်ချာနေသည်...", "AI matching your voice...")
        lifecycleScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    GeminiClient.voiceMatch(Prefs.apiKey(this@VoiceLabActivity), Prefs.model(this@VoiceLabActivity), sampleFile) { }
                }
                matchedVoice = r.first
                matchedStyle = r.second
                runOnUiThread {
                    resultText.text = "VOICE: " + matchedVoice + "\n" + L.s(this@VoiceLabActivity, "ပုံစံ", "STYLE") + ": " + matchedStyle
                    Toast.makeText(this@VoiceLabActivity, L.s(this@VoiceLabActivity, "မတ်ချာပြီးပါပြီ ✓", "Matched ✓"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = L.s(this@VoiceLabActivity, "မအောင်မြင်: ", "Failed: ") + (e.message ?: "")
                }
            }
        }
    }

    private fun useVoice() {
        if (matchedVoice.isEmpty()) {
            Toast.makeText(this, L.s(this, "အရင် AI MATCH လုပ်ပါ", "Run AI MATCH first"), Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.save(this, Prefs.KEY_VOICE, matchedVoice)
        Prefs.save(this, Prefs.KEY_CLONE_VOICE, matchedVoice)
        Prefs.save(this, Prefs.KEY_CLONE_STYLE, matchedStyle)
        Prefs.save(this, Prefs.KEY_CLONE_SAMPLE, sampleFile.absolutePath)
        if (matchedStyle.isNotEmpty()) {
            val cs = Prefs.customStyle(this)
            if (!cs.contains(matchedStyle)) {
                Prefs.save(this, Prefs.KEY_CUSTOM_STYLE, (if (cs.isEmpty()) "" else cs + " ") + matchedStyle)
            }
        }
        refreshResult()
        Toast.makeText(this, L.s(this, matchedVoice + " ကို default voice အဖြစ် သိမ်းပြီးပါပြီ ✓", "Saved " + matchedVoice + " as default voice ✓"), Toast.LENGTH_LONG).show()
    }

    private fun resetVoice() {
        Prefs.save(this, Prefs.KEY_VOICE, "Sulafat")
        Prefs.save(this, Prefs.KEY_CLONE_VOICE, "")
        Prefs.save(this, Prefs.KEY_CLONE_STYLE, "")
        matchedVoice = ""
        matchedStyle = ""
        refreshResult()
        Toast.makeText(this, L.s(this, "Sulafat ကို ပြန်သုံးပြီးပါပြီ", "Back to Sulafat"), Toast.LENGTH_SHORT).show()
    }

    private fun refreshResult() {
        if (!::resultText.isInitialized) return
        val cv = Prefs.cloneVoice(this)
        resultText.text = if (cv.isNotEmpty()) {
            L.s(this, "လက်ရှိအသံ: " + Prefs.voice(this) + " (voice lab ✓)", "Current voice: " + Prefs.voice(this) + " (voice lab ✓)")
        } else {
            L.s(this, "လက်ရှိအသံ: " + Prefs.voice(this), "Current voice: " + Prefs.voice(this))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 90 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        }
    }

    override fun onDestroy() {
        runCatching { recorder?.release() }
        runCatching { player?.release() }
        recorder = null
        player = null
        super.onDestroy()
    }
}
