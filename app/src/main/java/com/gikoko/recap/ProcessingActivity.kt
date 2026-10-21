package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.recap.RecapPipeline
import kotlinx.coroutines.launch
import java.io.File

class ProcessingActivity : AppCompatActivity() {

    private lateinit var stepText: TextView
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_processing)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        stepText = TextView(this)
        logText = TextView(this)
        build(root)

        val apiKey = intent.getStringExtra("api_key") ?: ""
        val voice = intent.getStringExtra("voice") ?: "Puck"
        val minutes = intent.getIntExtra("minutes", 0)
        val captions = intent.getBooleanExtra("captions", true)
        val source = intent.getStringExtra("source") ?: "youtube"
        val url = intent.getStringExtra("url") ?: ""
        val path = intent.getStringExtra("path") ?: ""

        lifecycleScope.launch {
            try {
                val out = if (source == "file") {
                    RecapPipeline.runFromFile(
                        this@ProcessingActivity,
                        File(path),
                        apiKey,
                        voice,
                        minutes,
                        captions,
                        { s -> runOnUiThread { stepText.text = s } },
                        { s -> runOnUiThread { logText.append(s + "\n") } }
                    )
                } else {
                    RecapPipeline.runFromYouTube(
                        this@ProcessingActivity,
                        url,
                        apiKey,
                        voice,
                        minutes,
                        { s -> runOnUiThread { stepText.text = s } },
                        { s -> runOnUiThread { logText.append(s + "\n") } }
                    )
                }
                val i = Intent(this@ProcessingActivity, ResultActivity::class.java)
                i.putExtra("video", out.video.absolutePath)
                i.putExtra("srt", out.srt.absolutePath)
                i.putExtra("title", out.title)
                startActivity(i)
                finish()
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                runOnUiThread {
                    stepText.text = "မအောင်မြင်ပါ"
                    AlertDialog.Builder(this@ProcessingActivity)
                        .setTitle("Recap မအောင်မြင်ပါ")
                        .setMessage(msg)
                        .setPositiveButton("ပြန်သွားမည်") { d, _ -> finish() }
                        .setNegativeButton("အသေးစိတ်ကြည့်မည်") { d, _ -> d.dismiss() }
                        .show()
                }
            }
        }
    }

    private fun build(root: LinearLayout) {
        val title = TextView(this)
        title.text = "RECAP ENGINE"
        title.setTextColor(Color.parseColor("#FFC940"))
        title.textSize = 20f
        title.gravity = Gravity.CENTER
        title.setTypeface(null, Typeface.BOLD)
        title.letterSpacing = 0.2f
        val tp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tp.setMargins(0, dp(56), 0, dp(8))
        root.addView(title, tp)

        stepText.text = "စတင်နေသည်..."
        stepText.setTextColor(Color.parseColor("#3DE8F8"))
        stepText.textSize = 15f
        stepText.gravity = Gravity.CENTER
        stepText.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.addView(stepText)

        val bar = ProgressBar(this)
        bar.indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFC940"))
        val bp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bp.gravity = Gravity.CENTER_HORIZONTAL
        bp.topMargin = dp(28)
        root.addView(bar, bp)

        logText.setTextColor(Color.parseColor("#8A93B2"))
        logText.textSize = 11f
        logText.setPadding(dp(20), dp(8), dp(20), dp(8))
        val scroll = ScrollView(this)
        scroll.addView(logText)
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        sp.topMargin = dp(20)
        root.addView(scroll, sp)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
