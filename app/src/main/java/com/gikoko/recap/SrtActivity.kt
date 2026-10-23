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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.gikoko.recap.recap.DownloadsUtil
import org.json.JSONObject
import java.io.File

class SrtActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open srt export")
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
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
        ht.text = "SUBTITLE SRT"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val info = note("Recap တစ်ခု ပြီးတိုင်း စာတန်း (SRT) က အလိုအလျောက် ဖန်တီးပြီး သိမ်းထားပါတယ်။ ဒီမှာ ရှေ့ရှိ recap တွေရဲ့ SRT ကို Downloads ထဲ SAVE ဒါမှမဟုတ် SHARE လို့ရပါတယ်။ YouTube / CapCut / Premiere တို့မှာ subtitle အဖြစ် တိုက်ရိုက်သုံးလို့ရတယ် (SubRip format)။")
        info.setPadding(dp(16), dp(8), dp(16), dp(4))
        root.addView(info)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list)

        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val arch = File(filesDir, "recap_archive")
        var dirs = arch.listFiles()?.filter { it.isDirectory } ?: emptyList()
        dirs = dirs.sortedByDescending { it.name }
        val withSrt = dirs.filter { File(it, "recap.srt").exists() }
        if (withSrt.isEmpty()) {
            val empty = TextView(this)
            empty.text = "SRT မရှိသေးပါ - recap အသစ်တစ်ခု လုပ်ပါ"
            empty.setTextColor(Color.parseColor("#8A93B2"))
            empty.textSize = 13f
            empty.gravity = Gravity.CENTER
            empty.setPadding(dp(16), dp(40), dp(16), dp(16))
            list.addView(empty)
            return
        }
        var shown = 0
        for (d in withSrt) {
            if (shown >= 10) break
            shown++
            var title = d.name
            val meta = File(d, "meta.json")
            if (meta.exists()) {
                try {
                    val j = JSONObject(meta.readText())
                    val t = j.optString("title", "")
                    if (t.isNotEmpty()) title = t
                } catch (e: Exception) {
                    // ignore
                }
            }
            val srt = File(d, "recap.srt")
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setBackgroundResource(R.drawable.bg_card)
            val cp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cp.setMargins(dp(16), dp(8), dp(16), dp(8))
            card.layoutParams = cp
            card.setPadding(dp(14), dp(12), dp(14), dp(12))

            val t1 = TextView(this)
            t1.text = title
            t1.setTextColor(Color.parseColor("#FFC940"))
            t1.textSize = 14f
            t1.setTypeface(null, Typeface.BOLD)
            t1.maxLines = 2
            card.addView(t1)

            val t2 = TextView(this)
            t2.text = d.name + "  -  " + String.format("%.1f", srt.length() / 1024.0) + " KB"
            t2.setTextColor(Color.parseColor("#8A93B2"))
            t2.textSize = 11f
            t2.setPadding(0, dp(4), 0, 0)
            card.addView(t2)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.topMargin = dp(10)
            card.addView(row, rp)

            val preview = btn("PREVIEW", R.drawable.bg_mode_card, "#C9D2EC")
            val save = btn("SAVE", R.drawable.bg_gold_btn, "#0A0A00")
            val share = btn("SHARE", R.drawable.bg_chip_selected, "#F2F5FF")
            row.addView(preview, btnParams())
            row.addView(save, btnParams())
            row.addView(share, btnParams())

            val srtText = runCatching { srt.readText() }.getOrDefault("")
            preview.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("SRT PREVIEW")
                    .setMessage(if (srtText.length > 3000) srtText.take(3000) + "\n..." else srtText)
                    .setPositiveButton("OK", null)
                    .show()
            }
            save.setOnClickListener {
                val name = "GIkokoSrt_" + d.name.replace(Regex("[^A-Za-z0-9_-]"), "") + ".srt"
                val ok = DownloadsUtil.saveFile(this, srt, name, "application/x-subrip")
                if (ok) {
                    Toast.makeText(this, "Downloads ထဲမှာ သိမ်းပြီးပါပြီ", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "သိမ်းမအောင်မြင်ပါ", Toast.LENGTH_LONG).show()
                }
            }
            share.setOnClickListener {
                try {
                    val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", srt)
                    val send = Intent(Intent.ACTION_SEND)
                    send.type = "application/x-subrip"
                    send.putExtra(Intent.EXTRA_STREAM, uri)
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, "SRT မျှဝေမည်"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show()
                }
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
