package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class QueueActivity : AppCompatActivity() {

    private val queueFiles = ArrayList<File>()
    private val queueNames = ArrayList<String>()
    private lateinit var listBox: LinearLayout
    private lateinit var countText: TextView
    private lateinit var seriesSwitch: SwitchCompat

    private val pickVideos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onVideosPicked(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open queue")
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
        ht.text = "VIDEO QUEUE"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c1 = card()
        c1.addView(sectionTitle("📋 QUEUE - Video တန်းစီ"))
        val addBtn = Button(this)
        addBtn.text = "➕ VIDEO ထပ်ထည့်မည် (MULTI PICK)"
        addBtn.isAllCaps = false
        addBtn.textSize = 13f
        addBtn.setTextColor(Color.parseColor("#0A0A00"))
        addBtn.setTypeface(null, Typeface.BOLD)
        addBtn.setBackgroundResource(R.drawable.bg_gold_btn)
        addBtn.setPadding(dp(8), dp(14), dp(8), dp(14))
        addBtn.setOnClickListener { pickVideos.launch(arrayOf("video/*")) }
        c1.addView(addBtn)
        countText = TextView(this)
        countText.setTextColor(Color.parseColor("#8A93B2"))
        countText.textSize = 12f
        countText.setPadding(dp(4), dp(8), dp(4), 0)
        c1.addView(countText)
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        c1.addView(listBox)
        val note1 = note("Video တွေ တစ်ပြိုင်တည်း ရွေးပါ - တစ်ခု ပြီးတာနဲ့ အလိုအလျောက် နောက် video ဆက်လုပ်ပါမယ်။ တစ်ခု ချော်ရင် ကျန်တာတွေ ဆက်လုပ်ပါမယ် (skip လို့ရ)။ အားလုံး ပြီးမှ result screen ပွင့်ပါမယ်။ အသံ/style/အချိန် တွေက Home Settings အတိုင်း အားလုံးမှာ အညံ့ အသုံးချပါမယ်။")
        note1.setPadding(0, dp(8), 0, 0)
        c1.addView(note1)
        root.addView(c1)

        val c2 = card()
        c2.addView(sectionTitle("🔗 SERIES LINK - အပိုင်းချိတ်ဆက်"))
        seriesSwitch = SwitchCompat(this)
        seriesSwitch.text = "နောက် recap က အရင်ပိုင်းအဆုံးကို ဆက်ပြောမယ်"
        seriesSwitch.textSize = 14f
        seriesSwitch.setTextColor(Color.parseColor("#F2F5FF"))
        c2.addView(seriesSwitch)
        val note2 = note("ဖွင့်ရင် - Video 2 ရဲ့ narration ထဲမှာ Video 1 အဆုံးသတ် အနှစ်ချုံ့ ချိတ်ဆက်ပြီး Part 1 → Part 2 ပုံစံ ဆက်ပြောသွားပါမယ် (AI ကို အရင်ပိုင်းအဆုံး ပေးပြီး ချိတ်ဆက်ခိုင်းတာပါ)။ ပိတ်ရင် - video တွေ အချင်းချင်း မဆိုင်ဘဲ တစ်ခုပြီးတာနဲ့ တစ်ခု အလိုအလျောက် ဆက်လုပ်ပေးပါမယ်။")
        note2.setPadding(0, dp(8), 0, 0)
        c2.addView(note2)
        root.addView(c2)

        val startBtn = Button(this)
        startBtn.text = "▶ START QUEUE"
        startBtn.isAllCaps = false
        startBtn.textSize = 16f
        startBtn.setTypeface(null, Typeface.BOLD)
        startBtn.setTextColor(Color.parseColor("#0A0A00"))
        startBtn.setBackgroundResource(R.drawable.bg_start_btn)
        startBtn.letterSpacing = 0.1f
        startBtn.setPadding(0, dp(16), 0, dp(16))
        val sp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sp.setMargins(dp(16), dp(16), dp(16), dp(8))
        root.addView(startBtn, sp)
        startBtn.setOnClickListener { startQueue() }

        val clearBtn = Button(this)
        clearBtn.text = "🗑 QUEUE အားလုံး ဖျက်မည်"
        clearBtn.isAllCaps = false
        clearBtn.textSize = 13f
        clearBtn.setTextColor(Color.parseColor("#F2F5FF"))
        clearBtn.setBackgroundResource(R.drawable.bg_chip_selected)
        clearBtn.setPadding(0, dp(12), 0, dp(12))
        val cp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cp.setMargins(dp(16), 0, dp(16), dp(8))
        root.addView(clearBtn, cp)
        clearBtn.setOnClickListener { clearQueue() }

        val warn = note("⚠️ Queue လုပ်နေစဉ် ဖုန်း screen မပိတ်ပါနဲ့ - app က screen အလိုအလျောက် ဖွင့်ထားပေးပါမယ်။ ဖုန်း RAM နည်းလို့ တစ်ချိန်ချင်း video ၁ ခုပဲ လုပ်ပါတယ်။ Queue mode မှာ PLAN EDITOR ကို ခဏ ပိတ်ထားပြီး အလိုအလျောက် ဆက်လုပ်ပါမယ်။ ပြီးတဲ့ video တွေက History ထဲမှာလည်း အလိုအလျောက် မှတ်တမ်းတင်ပါမယ်။")
        warn.setPadding(dp(16), dp(8), dp(16), dp(16))
        root.addView(warn)

        refreshList()
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

    private fun onVideosPicked(uris: List<Uri>) {
        Toast.makeText(this, uris.size.toString() + " ဖိုင် ကူးယူနေသည်...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val dir = File(filesDir, "queue")
                dir.mkdirs()
                val added = withContext(Dispatchers.IO) {
                    var n = 0
                    uris.forEachIndexed { i, uri ->
                        val disp = queryName(uri)
                        val ext = if (disp.lastIndexOf('.') > 0) disp.substring(disp.lastIndexOf('.')) else ".mp4"
                        val dst = File(dir, "q_" + System.currentTimeMillis() + "_" + i + ext)
                        contentResolver.openInputStream(uri)?.use { ins ->
                            dst.outputStream().use { ins.copyTo(it) }
                        }
                        if (dst.length() > 0L) {
                            queueFiles.add(dst)
                            queueNames.add(disp)
                            n++
                        }
                    }
                    n
                }
                runOnUiThread {
                    refreshList()
                    Toast.makeText(this@QueueActivity, "Queue ထဲ ထည့်ပြီးပါပြီ ✓ (" + added + " ဖိုင်)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@QueueActivity, "ကူးယူမအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshList() {
        listBox.removeAllViews()
        countText.text = if (queueFiles.isEmpty()) "Queue ဗလာ - VIDEO ထပ်ထည့်ပြီး START နှိပ်ပါ" else ("Queue ထဲမှာ video " + queueFiles.size + " ခု ရှိပါသည်")
        for (i in queueFiles.indices) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setBackgroundResource(R.drawable.bg_input)
            row.setPadding(dp(10), dp(8), dp(8), dp(8))
            val nm = TextView(this)
            nm.text = (i + 1).toString() + ". " + queueNames[i]
            nm.textSize = 12f
            nm.setTextColor(Color.parseColor("#F2F5FF"))
            nm.setSingleLine(true)
            nm.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            val np = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(nm, np)
            val rm = TextView(this)
            rm.text = "✕"
            rm.textSize = 16f
            rm.setTextColor(Color.parseColor("#FF5C5C"))
            rm.setPadding(dp(12), dp(4), dp(12), dp(4))
            rm.setOnClickListener {
                queueFiles.removeAt(i)
                queueNames.removeAt(i)
                refreshList()
            }
            row.addView(rm)
            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.topMargin = dp(6)
            listBox.addView(row, rp)
        }
    }

    private fun startQueue() {
        if (queueFiles.isEmpty()) {
            Toast.makeText(this, "Video အနည်းဆုံး ၁ ခု ရွေးပေးပါ", Toast.LENGTH_LONG).show()
            return
        }
        val key = Prefs.apiKey(this)
        if (key.isEmpty() && !(Prefs.provider(this) == "custom" && Prefs.ttsEngine(this) == "edge")) {
            Toast.makeText(this, "API key / Engine မသင့်ရသေးပါ - Settings (သို့) Tools → Edge TTS + Custom provider သုံးပါ", Toast.LENGTH_LONG).show()
            return
        }
        val arr = JSONArray()
        queueFiles.forEach { f -> arr.put(f.absolutePath) }
        val i = Intent(this, ProcessingActivity::class.java)
        i.putExtra("source", "file")
        i.putExtra("path", queueFiles[0].absolutePath)
        i.putExtra("queue", arr.toString())
        i.putExtra("queue_idx", 0)
        i.putExtra("queue_series", seriesSwitch.isChecked)
        startActivity(i)
        finish()
    }

    private fun clearQueue() {
        val dir = File(filesDir, "queue")
        dir.listFiles()?.forEach { f -> f.delete() }
        queueFiles.clear()
        queueNames.clear()
        refreshList()
        Toast.makeText(this, "Queue ဖျက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
    }
}
