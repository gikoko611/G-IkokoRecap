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

        val pt = TextView(this)
        pt.text = "💡 PROMPT IDEAS - ဘယ်လိုရေးရမသိရင် အောက်ကနေ ရွေးပါ"
        pt.setTextColor(Color.parseColor("#3DE8F8"))
        pt.textSize = 12f
        pt.letterSpacing = 0.1f
        pt.setPadding(dp(16), dp(6), dp(16), dp(4))
        root.addView(pt)

        val chipScroll = android.widget.HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipScroll.addView(chipRow)
        val csp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        csp.setMargins(dp(8), 0, dp(8), 0)
        root.addView(chipScroll, csp)
        promptIdeas.keys.forEach { cat ->
            val chip = TextView(this)
            chip.text = cat
            chip.textSize = 12f
            chip.setTextColor(Color.parseColor("#F2F5FF"))
            chip.setBackgroundResource(R.drawable.bg_chip_selected)
            chip.setPadding(dp(12), dp(8), dp(12), dp(8))
            chip.setOnClickListener { showPromptIdeas(cat) }
            val chp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            chp.marginEnd = dp(8)
            chipRow.addView(chip, chp)
        }
        val pn = TextView(this)
        pn.text = "Category နှိပ်ပြီး prompt ရွေးရင် ကူးယူပေးမယ် - zsky ရဲ့ input box မှာ ခဏနှိပ်ပြီး Paste လုပ်ပါ"
        pn.setTextColor(Color.parseColor("#8A93B2"))
        pn.textSize = 10f
        pn.setPadding(dp(16), dp(4), dp(16), dp(4))
        root.addView(pn)

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

    private val promptIdeas = linkedMapOf(
        "🎬 CINEMATIC" to listOf(
            "Cinematic drone shot flying over a neon-lit city at night, rain reflections on wet streets, anamorphic lens, 4K, film grain",
            "Epic wide shot of a lone warrior standing on a mountain peak at sunrise, golden hour lighting, cinematic color grade, slow camera push-in",
            "Slow motion close-up of coffee pouring into a glass cup, warm cafe lighting, shallow depth of field, macro lens, steam rising"
        ),
        "🌃 CYBERPUNK" to listOf(
            "Cyberpunk street market at night, holographic signs, rainy alley, neon purple and cyan lighting, cinematic tracking shot",
            "Futuristic samurai walking through neon Tokyo streets, glowing blade, reflections in puddles, cinematic slow motion",
            "Android woman face half-lit by neon light, rain dripping, cyberpunk aesthetic, ultra detailed, dramatic eyes"
        ),
        "🌿 NATURE" to listOf(
            "Aerial view of turquoise ocean waves crashing on a white sand beach, sunny day, drone shot, 4K, vibrant colors",
            "Timelapse of clouds rolling over a green mountain valley at golden hour, birds flying, serene atmosphere",
            "Close-up of a hummingbird drinking from a red flower, ultra slow motion, water droplets sparkling, natural light"
        ),
        "🐉 ANIME" to listOf(
            "Anime style girl standing under cherry blossom trees, wind blowing petals, soft warm lighting, Studio Ghibli inspired, detailed background",
            "Anime action scene, hero running across city rooftops at sunset, dramatic speed lines, dynamic pose, vivid colors",
            "Cozy anime cafe interior, warm light, cat sleeping on a chair, Makoto Shinkai style, detailed clouds outside the window"
        ),
        "🎮 3D GAME" to listOf(
            "3D render of a fantasy knight character, glowing runes on armor, Unreal Engine 5, studio lighting, ultra detailed",
            "Game environment: futuristic space station corridor, volumetric lighting, sci-fi props, Unreal Engine 5 cinematic",
            "Stylized 3D floating island in the sky with waterfalls, Pixar style, soft lighting, cute details, wide shot"
        ),
        "📱 ADS / PRODUCT" to listOf(
            "Product ad: luxury perfume bottle rotating on black marble, dramatic studio lighting, water splash, macro details",
            "Fresh burger commercial, steam rising, ingredients flying in slow motion, dark moody background, appetizing, macro lens",
            "Smartphone floating with glowing hologram UI around it, clean studio background, premium tech commercial style"
        ),
        "🍿 MOVIE STYLE" to listOf(
            "Superhero movie scene: dramatic hero pose, explosion in the background, cinematic lighting, Marvel movie style, IMAX",
            "Horror movie scene: dark forest at midnight, thick fog, flashlight beam cutting through, found footage style, tense",
            "Sci-fi movie shot: giant spaceship descending over a city skyline, people looking up, epic scale, IMAX style"
        ),
        "🛕 MYANMAR GOLD" to listOf(
            "Golden pagoda at sunrise, rays of light through morning mist, drone orbit shot, majestic, cinematic 4K",
            "Traditional Myanmar water festival celebration, slow motion water splashes, joyful crowd, golden light, festive",
            "Ancient Bagan temples with hot air balloons at dawn, misty landscape, cinematic wide shot, warm tones"
        )
    )

    private fun showPromptIdeas(cat: String) {
        val list = promptIdeas[cat] ?: return
        val labels = list.mapIndexed { i, s -> (i + 1).toString() + ". " + s }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("💡 " + cat + " - နှိပ်ရင် Prompt ကူးယူမယ်")
            .setItems(labels) { _, which ->
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("prompt", list[which]))
                statusText.text = "Prompt ကူးယူပြီး ✓ (" + cat + ") - zsky input မှာ paste လုပ်ပါ"
                Toast.makeText(this, "Prompt ကူးယူပြီး ✓ - zsky input box မှာ ခဏနှိပ်ပြီး Paste လုပ်ပါ", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("ပိတ်", null)
            .show()
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
