package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
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
        ht.text = "HISTORY"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val scroll = ScrollView(this)
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(scroll, sp)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)

        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val arch = File(filesDir, "recap_archive")
        var dirs = arch.listFiles()?.filter { it.isDirectory } ?: emptyList()
        dirs = dirs.sortedByDescending { it.name }
        if (dirs.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Recap မှတ်တမ်း မရှိသေးပါ။ အသစ်တစ်ခု လုပ်ကြည့်ပါ။"
            empty.setTextColor(Color.parseColor("#8A93B2"))
            empty.textSize = 13f
            empty.gravity = Gravity.CENTER
            empty.setPadding(dp(16), dp(40), dp(16), dp(16))
            list.addView(empty)
            return
        }
        for (d in dirs) {
            val video = File(d, "recap.mp4")
            if (!video.exists()) continue
            var title = d.name
            var summary = ""
            var src = ""
            val meta = File(d, "meta.json")
            if (meta.exists()) {
                try {
                    val j = JSONObject(meta.readText())
                    title = j.optString("title", d.name)
                    summary = j.optString("summary", "")
                    src = j.optString("source", "")
                } catch (e: Exception) {
                    // ignore
                }
            }
            val srt = File(d, "recap.srt")
            val mb = video.length() / (1024.0 * 1024.0)
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setBackgroundResource(R.drawable.bg_card)
            val cp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cp.setMargins(dp(16), dp(8), dp(16), dp(8))
            card.layoutParams = cp
            card.setPadding(dp(16), dp(14), dp(16), dp(14))

            val t1 = TextView(this)
            t1.text = title
            t1.setTextColor(Color.parseColor("#FFC940"))
            t1.textSize = 15f
            t1.setTypeface(null, Typeface.BOLD)
            t1.maxLines = 2
            card.addView(t1)

            val t2 = TextView(this)
            t2.text = d.name + (if (src.isNotEmpty()) "  -  " + src else "") + "  -  " + String.format("%.1f", mb) + " MB"
            t2.setTextColor(Color.parseColor("#8A93B2"))
            t2.textSize = 11f
            t2.setPadding(0, dp(4), 0, 0)
            card.addView(t2)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.topMargin = dp(10)
            card.addView(row, rp)

            val play = btn("Play", R.drawable.bg_gold_btn, "#0A0A00")
            val share = btn("Share", R.drawable.bg_chip_selected, "#F2F5FF")
            val del = btn("Delete", R.drawable.bg_mode_card, "#C9D2EC")
            row.addView(play, btnParams())
            row.addView(share, btnParams())
            row.addView(del, btnParams())

            val srtPath = if (srt.exists()) srt.absolutePath else ""
            val summaryFinal = summary
            play.setOnClickListener {
                val i = Intent(this, ResultActivity::class.java)
                i.putExtra("video", video.absolutePath)
                i.putExtra("srt", srtPath)
                i.putExtra("title", title)
                i.putExtra("summary", summaryFinal)
                startActivity(i)
            }
            share.setOnClickListener {
                try {
                    val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", video)
                    val send = Intent(Intent.ACTION_SEND)
                    send.type = "video/mp4"
                    send.putExtra(Intent.EXTRA_STREAM, uri)
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, "Recap မျှဝေမည်"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show()
                }
            }
            del.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("ဖျက်မည်လား")
                    .setMessage("ဒီ recap ကို ဖျက်ပစ်မှာလား")
                    .setPositiveButton("ဖျက်မည်") { _, _ ->
                        d.deleteRecursively()
                        refresh()
                    }
                    .setNegativeButton("မဖျက်ဘူး", null)
                    .show()
            }
            list.addView(card)
        }
        val padBottom = TextView(this)
        val pb = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20))
        list.addView(padBottom, pb)
    }

    private fun btn(text: String, bg: Int, color: String): Button {
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
}
