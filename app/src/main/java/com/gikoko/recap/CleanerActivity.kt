package com.gikoko.recap

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class CleanerActivity : AppCompatActivity() {

    private lateinit var listBox: LinearLayout
    private lateinit var totalText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open cleaner")
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    private fun dirSize(f: File): Long {
        var s = 0L
        val list = f.listFiles() ?: return 0
        for (x in list) s += if (x.isDirectory) dirSize(x) else x.length()
        return s
    }

    private fun sizeText(s: Long): String {
        if (s <= 0) return "ဗလာ"
        return if (s > 1024L * 1024L) String.format("%.1f MB", s / (1024.0 * 1024.0)) else String.format("%d KB", s / 1024L)
    }

    private fun cleanDir(f: File) {
        f.listFiles()?.forEach { x ->
            if (x.isDirectory) cleanDir(x)
            x.delete()
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
        ht.text = "STORAGE CLEANER"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        totalText = TextView(this)
        totalText.setTextColor(Color.parseColor("#3DE8F8"))
        totalText.textSize = 13f
        totalText.gravity = Gravity.CENTER
        totalText.setPadding(dp(16), dp(8), dp(16), dp(4))
        root.addView(totalText)

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        root.addView(listBox)

        val n = TextView(this)
        n.text = "⚠️ QUEUE နဲ့ MERGER တွေက video တွေကို အလုပ်လုပ်ဖို့ ယာယီကူးထားတာပါ — အလုပ်ပြီးသွားရင် ဒါတွေက မလိုအပ်တော့ပါဘူး။ ဒါပေမယ့် queue/merge နောက်ကျနေအလုပ်များရှိနေရင် မဖျက်ပါနဲ့။ ဖျက်လိုက်တဲ့ဖိုင်တွေကို ပြန်ရမှာ မဟုတ်ပါဘူး။ အလုပ်ပြီးတဲ့ video အစစ်တွေကတော့ Downloads ထဲမှာ ကိုယ််စဉ်ကိုယ်ပိုင် သိမ်းနေတာပါ။"
        n.setTextColor(Color.parseColor("#8A93B2"))
        n.textSize = 11f
        n.setPadding(dp(16), dp(10), dp(16), dp(16))
        root.addView(n)

        refresh()
    }

    private fun targets(): List<Pair<String, File>> {
        return listOf(
            "CACHE - app ယာယီဖိုင်များ" to cacheDir,
            "QUEUE - VIDEO QUEUE ကူးထားတဲ့ video တွေ" to File(filesDir, "queue"),
            "MERGE - MERGER ကူးထားတဲ့ video တွေ" to File(filesDir, "merge")
        )
    }

    private fun refresh() {
        listBox.removeAllViews()
        var total = 0L
        for ((label, dir) in targets()) {
            val size = dirSize(dir)
            total += size
            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setBackgroundResource(R.drawable.bg_card)
            card.setPadding(dp(12), dp(10), dp(12), dp(10))
            val cp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cp.setMargins(dp(16), dp(6), dp(16), dp(6))
            card.layoutParams = cp

            val tv = TextView(this)
            tv.text = label + "\n" + sizeText(size)
            tv.textSize = 12f
            tv.setTextColor(Color.parseColor("#F2F5FF"))
            val tp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            card.addView(tv, tp)

            val btn = TextView(this)
            btn.text = "🧹 CLEAN"
            btn.textSize = 12f
            btn.gravity = Gravity.CENTER
            btn.setTextColor(Color.parseColor("#0A0A00"))
            btn.setTypeface(null, Typeface.BOLD)
            btn.setBackgroundResource(R.drawable.bg_gold_btn)
            btn.setPadding(dp(12), dp(10), dp(12), dp(10))
            btn.setOnClickListener {
                cleanDir(dir)
                Toast.makeText(this, "ရှင်းလင်းပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
                refresh()
            }
            card.addView(btn)

            listBox.addView(card)
        }
        totalText.text = "စုစုပေါင်း ပြန်လည်ရယူနိုင်တဲ့နေရာ: " + sizeText(total)
    }
}
