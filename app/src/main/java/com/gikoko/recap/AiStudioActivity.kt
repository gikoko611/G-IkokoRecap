package com.gikoko.recap

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.recap.DownloadsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class AiStudioActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var statusText: TextView
    private var dlCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open ai studio")
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val hp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        ht.text = "AI STUDIO (FREE)"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        statusText = TextView(this)
        statusText.setTextColor(Color.parseColor("#3DE8F8"))
        statusText.textSize = 11f
        statusText.setPadding(dp(16), 0, dp(16), 0)
        root.addView(statusText)

        val n = TextView(this)
        n.text = "zsky.ai ရဲ့ unlimited free tier — image / video ဆွဲလို့ရပါတယ် (API key မလိုပါ)။ ပုံ (ဒါမှမဟုတ် video) ဆွဲပြီး download နှိပ်ရင် app က Downloads ထဲ သိမ်းပေးပါမယ်။ ဒါက zsky ရဲ့ website ကို app ထဲမှာ ဖွင့်ပြတာပါ — site လိုအပ်ချက်အရ တခါတရံ နှေးနိုင်ပါတယ်။"
        n.setTextColor(Color.parseColor("#8A93B2"))
        n.textSize = 11f
        n.setPadding(dp(16), 0, dp(16), dp(8))
        root.addView(n)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true
        web.webViewClient = WebViewClient()
        web.setDownloadListener { url, _, _, mime, _ -> saveFromStudio(url, mime ?: "") }
        val wp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        wp.setMargins(dp(8), 0, dp(8), dp(16))
        root.addView(web, wp)

        web.loadUrl("https://zsky.ai/create")
    }

    private fun saveFromStudio(url: String, mime: String) {
        if (!url.startsWith("http")) {
            Toast.makeText(this, "ပုံပေါ်မှာ ခဏနှိပ်ဖိထားပြီး Save image ကို ရွေးပါ", Toast.LENGTH_LONG).show()
            return
        }
        dlCount++
        val isVideo = mime.contains("video")
        val ext = when {
            isVideo -> ".mp4"
            mime.contains("png") -> ".png"
            mime.contains("webp") -> ".webp"
            else -> ".jpg"
        }
        val outMime = when {
            isVideo -> "video/mp4"
            mime.contains("png") -> "image/png"
            mime.contains("webp") -> "image/webp"
            else -> "image/jpeg"
        }
        val name = "AIstudio_" + System.currentTimeMillis() + ext
        statusText.text = "Download လုပ်နေသည်... (" + dlCount + ")"
        lifecycleScope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(600, TimeUnit.SECONDS)
                        .build()
                    val tmp = File(cacheDir, "studio_dl" + ext)
                    if (tmp.exists()) tmp.delete()
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP " + resp.code)
                        resp.body!!.byteStream().use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                    }
                    tmp
                }
                val ok = withContext(Dispatchers.IO) {
                    DownloadsUtil.saveFile(this@AiStudioActivity, f, name, outMime)
                }
                if (ok) {
                    statusText.text = "သိမ်းပြီးပါပြီ ✓ " + name + " (Downloads)"
                    Toast.makeText(this@AiStudioActivity, "Downloads ထဲမှာ သိမ်းပြီးပါပြီ ✓", Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "သိမ်းမှု မအောင်မြင်ပါ"
                }
            } catch (e: Exception) {
                statusText.text = "Download မအောင်မြင်: " + (e.message ?: "")
            }
        }
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
