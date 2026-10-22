package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.File

class StudioActivity : AppCompatActivity() {

    private val voices = listOf("Sulafat", "Vindemiatrix", "Erinome", "Aoede", "Kore", "Leda", "Zephyr", "Despina", "Charon", "Iapetus", "Achird", "Orus", "Puck", "Fenrir", "Algenib", "Rasalgethi")
    private lateinit var bgmSwitch: SwitchCompat
    private lateinit var bgmStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open studio")
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
        ht.text = "RECAP STUDIO"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c1 = card()
        c1.addView(sectionTitle(L.s(this, "RECAP အချိန် - SMART LENGTH", "RECAP LENGTH - SMART")))
        val minVals = intArrayOf(-1, 1, 3, 5)
        val mg = ChipGroup(this)
        mg.isSingleSelection = true
        mg.isSelectionRequired = true
        listOf("SMART", "1 MIN", "3 MIN", "5 MIN").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 43000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.saveInt(this, Prefs.KEY_MINUTES, minVals[i])
            }
            mg.addView(ch)
        }
        val curMin = Prefs.minutes(this)
        val mi = minVals.indexOf(curMin)
        mg.check(if (mi >= 0) 43000 + mi else 43000)
        c1.addView(mg)
        c1.addView(note(L.s(this, "SMART ဖြစ်ရင် AI က အကြောင်းအရာအလိုက် အသင့်တော်ဆုံး အချိန် (၁-၅ မိနစ်) ကိုယ်တိုင် ရွေးမယ်။", "SMART lets the AI pick the best length (1-5 min) for the content.")))
        root.addView(c1)

        val c2 = card()
        c2.addView(sectionTitle(L.s(this, "AUTO CLIP SHORTS", "AUTO CLIP SHORTS")))
        c2.addView(switchRow(L.s(this, "ပြီးလို့ရင် vertical shorts clip အလိုအလျောက် ထုတ်မည်", "Auto-create a vertical shorts clip"), Prefs.KEY_SHORTS, false))
        c2.addView(note(L.s(this, "အသံအရှည်ဆုံး segment ၃ ခုကို 1080×1920 vertical shorts အဖြစ် ပေါင်းပြီး ပေးမယ် - TikTok / Reels / Shorts အတွက်။", "Top-3 longest segments combined into a 1080x1920 vertical clip for TikTok/Reels/Shorts.")))
        root.addView(c2)

        val c3 = card()
        c3.addView(sectionTitle(L.s(this, "DIALOGUE - SPEAKER 2", "DIALOGUE - SPEAKER 2")))
        c3.addView(switchRow(L.s(this, "HOST / GUEST နှစ်ယောက်နဲ့ စကားပြောပုံစံ recap", "Host/Guest two-voice dialogue recap"), Prefs.KEY_DIALOGUE, false))
        c3.addView(sectionTitle(L.s(this, "SPEAKER 2 အသံ", "SPEAKER 2 VOICE")))
        val vg = ChipGroup(this)
        vg.isSingleSelection = true
        vg.isSelectionRequired = true
        voices.forEachIndexed { i, v ->
            val ch = chipOf(v)
            ch.id = 43100 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.save(this, Prefs.KEY_VOICE2, v)
            }
            vg.addView(ch)
        }
        val curV2 = Prefs.voice2(this)
        val vi = voices.indexOf(curV2)
        vg.check(if (vi >= 0) 43100 + vi else 43104)
        c3.addView(vg)
        c3.addView(note(L.s(this, "ခုန်တချက်စီမှာ အသံနှစ်ခု အလှည့်ကျ ပြောပြမယ် - Speaker 1 = default voice၊ Speaker 2 = ဒီ voice။", "Voices alternate per segment: Speaker 1 = default voice, Speaker 2 = this voice.")))
        root.addView(c3)

        val c4 = card()
        c4.addView(sectionTitle(L.s(this, "BGM AUTO MIX", "BGM AUTO MIX")))
        bgmSwitch = switchRow(L.s(this, "နောက်ခံတေးဂီတ အလိုအလျောက် ရောစပ်မည်", "Auto-mix background music"), Prefs.KEY_BGM_ON, false)
        c4.addView(bgmSwitch)
        val rowB = LinearLayout(this)
        rowB.orientation = LinearLayout.HORIZONTAL
        val pickB = flatBtn(L.s(this, "တေးဂီတ ရွေးမည်", "PICK MUSIC"), R.drawable.bg_chip_selected, "#F2F5FF")
        val clearB = flatBtn(L.s(this, "ဖျက်မည်", "CLEAR"), R.drawable.bg_mode_card, "#C9D2EC")
        rowB.addView(pickB, btnParams())
        rowB.addView(clearB, btnParams())
        val rowBP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rowBP.topMargin = dp(10)
        c4.addView(rowB, rowBP)
        pickB.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("audio/*"), 79)
        }
        clearB.setOnClickListener {
            File(filesDir, "bgm_music").delete()
            Prefs.save(this, Prefs.KEY_BGM_PATH, "")
            refreshBgm()
            Toast.makeText(this, L.s(this, "BGM ဖျက်ပြီးပါပြီ", "BGM removed"), Toast.LENGTH_SHORT).show()
        }
        bgmStatus = TextView(this)
        bgmStatus.setTextColor(Color.parseColor("#C9D2EC"))
        bgmStatus.textSize = 12f
        val bsP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        bsP.topMargin = dp(8)
        c4.addView(bgmStatus, bsP)
        c4.addView(sectionTitle(L.s(this, "BGM အသံအတိုး", "BGM VOLUME")))
        val volVals = listOf("0.12", "0.2", "0.3")
        val vG = ChipGroup(this)
        vG.isSingleSelection = true
        vG.isSelectionRequired = true
        listOf("LOW", "MEDIUM", "HIGH").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 43200 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) Prefs.save(this, Prefs.KEY_BGM_VOL, volVals[i])
            }
            vG.addView(ch)
        }
        val curVol = volVals.indexOf(Prefs.bgmVol(this))
        vG.check(if (curVol >= 0) 43200 + curVol else 43201)
        c4.addView(vG)
        c4.addView(note(L.s(this, "Recap အသံပေါ်မှာ BGM ကို အသံနိမ့်လေးနဲ့ ပြန့်ပြောင်းရောပေးမယ်။", "BGM loops quietly under the narration.")))
        root.addView(c4)

        val c5 = card()
        c5.addView(sectionTitle(L.s(this, "PODCAST / MP3 EXPORT", "PODCAST / MP3 EXPORT")))
        c5.addView(switchRow(L.s(this, "ပြီးလို့ရင် podcast audio file ပါ ထုတ်မည်", "Also export podcast audio"), Prefs.KEY_PODCAST, false))
        c5.addView(note(L.s(this, "Recap အသံနဲ့ intro/outro ကို podcast audio (M4A) အဖြစ် ထုတ်ပေးမယ် - ကိုယ်ပိုင် podcast တင်ဖို့။", "Exports the narration as a podcast-ready audio file (M4A).")))
        root.addView(c5)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
        refreshBgm()
    }

    private fun refreshBgm() {
        if (!::bgmStatus.isInitialized) return
        val p = Prefs.bgmPath(this)
        bgmStatus.text = if (p.isNotEmpty() && File(p).exists()) {
            L.s(this, "BGM ရှိပြီးသား ✓", "BGM ready ✓")
        } else {
            L.s(this, "BGM မရွေးရသေးပါ", "No music picked yet")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 79 && resultCode == -1 && data?.data != null) {
            runCatching {
                val dst = File(filesDir, "bgm_music")
                contentResolver.openInputStream(data.data!!)?.use { ins ->
                    dst.outputStream().use { ins.copyTo(it) }
                }
                if (dst.length() > 0L) {
                    Prefs.save(this, Prefs.KEY_BGM_PATH, dst.absolutePath)
                    if (::bgmSwitch.isInitialized) bgmSwitch.isChecked = true
                    refreshBgm()
                    Toast.makeText(this, L.s(this, "တေးဂီတ သိမ်းပြီးပါပြီ", "Music saved"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBgm()
    }
}
