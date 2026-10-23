package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.recap.DownloadsUtil
import com.gikoko.recap.recap.WavUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class BgmLibraryActivity : AppCompatActivity() {

    private data class BgmStyle(val key: String, val emoji: String, val desc: String)

    private val styles = listOf(
        BgmStyle("CHILL", "🌙", "မှိန်ဖွေ အေးမြသလို အသံ - video recap အောက်ခံအတွက်"),
        BgmStyle("CYBER", "🌃", "cyberpunk ခေတ်သစ် beat - gaming/tech recap အတွက်"),
        BgmStyle("NATURE", "🌿", "သဘာဝ လေသံ + ငှက်သံ - ငြိမ်သက်တဲ့ recap အတွက်"),
        BgmStyle("LOFI", "🎧", "lo-fi hip hop လေးနက် သီချင်း - ဖတ်စရာ/ပြောစရာ recap အတွက်")
    )

    private val pickMode by lazy { intent?.getBooleanExtra("pick", false) ?: false }

    private var player: MediaPlayer? = null
    private var busy = false
    private var playingKey: String? = null

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private val statusOf = HashMap<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open bgm library")
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
        ht.text = if (pickMode) "BGM LIBRARY - ရွေးမည်" else "BGM LIBRARY"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val cs = card()
        progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        progressBar.visibility = View.GONE
        cs.addView(progressBar)
        statusText = TextView(this)
        statusText.setTextColor(Color.parseColor("#8A93B2"))
        statusText.textSize = 12f
        statusText.setPadding(0, dp(6), 0, 0)
        statusText.text = if (pickMode) "နားထောင်ကြည့်ပြီး သင့်နှစ်သက်တဲ့ BGM ရွေးပါ" else "App အတွင်းမှာပဲ အသံတွေ ဖန်တီးတာ - KEY မလို၊ NET မလိုပါ - PREVIEW နှိပ်ပြီး နားထောင်ပါ"
        cs.addView(statusText)
        root.addView(cs)

        for (s in styles) {
            val c = card()
            c.addView(sectionTitle(s.emoji + " " + s.key + "  (30 sec)"))
            c.addView(note(s.desc))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.topMargin = dp(10)
            row.layoutParams = rp

            val prev = flatBtn("▶ နားထောင်", R.drawable.bg_chip_selected, "#F2F5FF")
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(prev, lp)
            prev.setOnClickListener { preview(s) }

            val stop = flatBtn("⏹ ရပ်", R.drawable.bg_chip_selected, "#F2F5FF")
            val sp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            sp.leftMargin = dp(6)
            row.addView(stop, sp)
            stop.setOnClickListener { stopPlayer() }

            val use = flatBtn(if (pickMode) "✓ ရွေးမည်" else "⬇ သိမ်းမည်", R.drawable.bg_gold_btn, "#0A0A00")
            val up = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            up.leftMargin = dp(6)
            row.addView(use, up)
            use.setOnClickListener { use(s) }

            c.addView(row)
            val st = note("")
            st.setPadding(0, dp(6), 0, 0)
            c.addView(st)
            statusOf[s.key] = st
            root.addView(c)
        }
    }

    private fun fileFor(key: String): File {
        val dir = File(cacheDir, "bgmlib")
        dir.mkdirs()
        return File(dir, key.lowercase() + ".wav")
    }

    private fun ensure(style: BgmStyle, done: (File) -> Unit) {
        if (busy) {
            Toast.makeText(this, "အသံ ဖန်တီးနေဆဲ - ခဏစောင့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val f = fileFor(style.key)
        if (f.exists() && f.length() > 100L) {
            done(f)
            return
        }
        busy = true
        progressBar.visibility = View.VISIBLE
        statusText.text = style.key + " အသံ ဖန်တီးနေသည်..."
        val key = style.key
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val wav = synth(key)
                    FileOutputStream(f).use { o -> o.write(wav) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            busy = false
            progressBar.visibility = View.GONE
            if (ok) {
                statusText.text = style.key + " အသံ ပြီးပါပြီ ✓"
                done(f)
            } else {
                statusText.text = "အသံ ဖန်တီးမအောင်မြင်ပါ"
                Toast.makeText(this@BgmLibraryActivity, "အသံ ဖန်တီးမအောင်မြင်ပါ", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun preview(style: BgmStyle) {
        ensure(style) { f ->
            stopPlayer()
            try {
                val mp = MediaPlayer()
                mp.setDataSource(f.absolutePath)
                mp.setOnPreparedListener { p -> p.start() }
                mp.setOnCompletionListener { p ->
                    runCatching { p.release() }
                    if (playingKey == style.key) playingKey = null
                    statusOf[style.key]?.text = ""
                }
                mp.prepareAsync()
                player = mp
                playingKey = style.key
                statusOf[style.key]?.text = "နားထောင်နေသည်... " + style.key
                Toast.makeText(this, style.key + " စပြီး ဖျော်ပါ ✨", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "ဖျော်မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun use(style: BgmStyle) {
        ensure(style) { f ->
            if (pickMode) {
                stopPlayer()
                val i = Intent()
                i.putExtra("path", f.absolutePath)
                setResult(-1, i)
                finish()
            } else {
                val name = "BGM_" + style.key + "_" + System.currentTimeMillis() + ".wav"
                val ok = DownloadsUtil.saveFile(this, f, name, "audio/wav")
                if (ok) {
                    statusOf[style.key]?.text = "သိမ်းပြီးပါပြီ ✓ - Downloads မှာ " + name
                    Toast.makeText(this, "ပြီးပါပြီ - Downloads မှာ " + name, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "သိမ်းရာမှာ မအောင်မြင်ပါ", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopPlayer() {
        runCatching { player?.release() }
        player = null
        if (playingKey != null) {
            statusOf[playingKey!!]?.text = ""
            playingKey = null
        }
    }

    private fun synth(key: String): ByteArray {
        val rate = 22050
        val durSec = 30.0
        val n = (rate.toDouble() * durSec).toInt()
        val pcm = ByteArray(n * 2)
        for (i in 0 until n) {
            val t = i.toDouble() / rate.toDouble()
            var s = when (key) {
                "CYBER" -> synthCyber(t)
                "NATURE" -> synthNature(t)
                "LOFI" -> synthLofi(t)
                else -> synthChill(t)
            }
            val env = min(1.0, min(t / 2.0, (durSec - t) / 3.0))
            s *= max(0.0, env)
            if (s > 1.0) s = 1.0
            if (s < -1.0) s = -1.0
            val v = (s * 15000.0).toInt()
            pcm[i * 2] = (v and 0xff).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        return WavUtil.pcmToWav(pcm, rate)
    }

    private fun tone(t: Double, freq: Double): Double {
        return sin(2.0 * PI * freq * t)
    }

    private fun synthChill(t: Double): Double {
        val prog = doubleArrayOf(220.0, 174.61, 130.81, 196.0)
        val seg = ((t / 7.5).toInt()) % 4
        val f = prog[seg] / 2.0
        var s = tone(t, f) * 0.30 + tone(t, f * 1.5) * 0.20 + tone(t, f * 2.0) * 0.10 + tone(t, f * 2.5) * 0.05
        s *= 0.85 + 0.15 * sin(2.0 * PI * 0.12 * t)
        val beat = t * 1.6
        val bp = beat % 1.0
        if (bp < 0.18) s += sin(2.0 * PI * (60.0 * bp + 130.0 * bp * bp)) * (1.0 - bp / 0.18) * 0.12
        return s
    }

    private fun synthCyber(t: Double): Double {
        val b = t * 2.2
        val pulse = if (b % 1.0 < 0.5) 1.0 else 0.3
        var s = tone(t, 55.0) * 0.26 * pulse + tone(t, 110.0) * 0.14 * pulse
        val notes = doubleArrayOf(220.0, 261.63, 329.63, 440.0, 392.0)
        val step = b.toInt() % 5
        val seg = b % 1.0
        val env = if (seg < 0.25) (1.0 - seg / 0.25) else 0.0
        val nf = notes[step]
        s += (tone(t, nf) + 0.35 * tone(t, nf * 2.0)) * 0.14 * env
        if (step % 2 == 1 && seg < 0.06) s += sin(t * 90000.0) * 0.05
        return s
    }

    private fun synthNature(t: Double): Double {
        var s = tone(t, 110.0) * 0.11 + tone(t, 165.0) * 0.07
        s += sin(t * 850.0) * (0.5 + 0.5 * sin(2.0 * PI * 0.07 * t)) * 0.045
        s += sin(t * 2300.0) * (0.5 + 0.5 * sin(2.0 * PI * 0.11 * t + 1.3)) * 0.028
        val ph = t % 6.0
        if (ph < 0.12 || (ph > 0.9 && ph < 0.98)) {
            val bt = if (ph < 0.2) ph else ph - 0.9
            val len = if (ph < 0.2) 0.12 else 0.08
            val amp = if (ph < 0.2) 0.09 else 0.06
            s += sin(2.0 * PI * (2400.0 * bt + 7000.0 * bt * bt)) * sin(PI * bt / len) * amp
        }
        return s
    }

    private fun synthLofi(t: Double): Double {
        val spb = 60.0 / 72.0
        val kb = (t / spb) % 1.0
        var s = 0.0
        if (kb < 0.3) s += sin(2.0 * PI * (52.0 * kb + 140.0 * kb * kb)) * (1.0 - kb / 0.3) * 0.26
        val chords = arrayOf(
            doubleArrayOf(130.81, 164.81, 196.0),
            doubleArrayOf(116.54, 146.83, 174.61),
            doubleArrayOf(98.0, 123.47, 146.83),
            doubleArrayOf(87.31, 110.0, 130.81)
        )
        val bar = ((t / (spb * 4.0)).toInt()) % 4
        var pad = 0.0
        for (fr in chords[bar]) pad += tone(t, fr) * 0.065
        pad *= 0.8 + 0.2 * sin(2.0 * PI * 0.2 * t)
        s += pad
        val cp = t % 0.37
        if (cp < 0.004) s += sin(t * 180000.0) * 0.04
        val snb = (t / spb) % 2.0
        if (snb > 1.0 && snb - 1.0 < 0.12) s += sin(t * 60000.0) * 0.03 * (1.0 - (snb - 1.0) / 0.12)
        return s
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }
}
