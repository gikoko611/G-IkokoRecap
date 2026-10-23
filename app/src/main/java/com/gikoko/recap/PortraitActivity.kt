package com.gikoko.recap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.recap.DownloadsUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class PortraitActivity : AppCompatActivity() {

    private val styles = listOf("PROFESSIONAL", "CINEMATIC", "ANIME", "3D", "FANTASY")
    private val styleIn = listOf(
        ", professional headshot portrait, studio lighting, 85mm lens, sharp focus, ultra detailed",
        ", cinematic portrait, dramatic rim lighting, film look, shallow depth of field, high quality",
        ", anime style portrait, vibrant colors, detailed illustration, studio quality",
        ", 3D character portrait render, Pixar style, soft cinematic lighting, ultra detailed",
        ", fantasy portrait, magical glow, ethereal atmosphere, intricate details, high quality"
    )

    private var styleIdx = 0
    private var busy = false
    private var bmp: Bitmap? = null

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var descInput: EditText
    private lateinit var preview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open portrait maker")
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
        ht.text = "AI PORTRAIT MAKER"
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
        statusText.text = "KEY မလိုပါ - AI က portrait ကိုယ်တိုင်ဆွဲပေးမယ်"
        cs.addView(statusText)
        root.addView(cs)

        val cd = card()
        cd.addView(sectionTitle("🧑 ဘယ်လို portrait လဲ (English လို ဖော်ပြပါ)"))
        descInput = EditText(this)
        descInput.hint = "ဥပမာ - young man, black suit, smiling, city night background"
        descInput.setBackgroundResource(R.drawable.bg_input)
        descInput.setTextColor(Color.parseColor("#F2F5FF"))
        descInput.setHintTextColor(Color.parseColor("#5A6480"))
        descInput.textSize = 14f
        descInput.setSingleLine(false)
        descInput.minLines = 2
        descInput.setPadding(dp(12), dp(12), dp(12), dp(12))
        cd.addView(descInput)
        cd.addView(sectionTitle("🎨 STYLE"))
        val sg = ChipGroup(this)
        sg.isSingleSelection = true
        sg.isSelectionRequired = true
        styles.forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 67000 + i
            ch.setOnCheckedChangeListener { _, checked -> if (checked) styleIdx = i }
            sg.addView(ch)
        }
        sg.check(67000)
        cd.addView(sg)
        root.addView(cd)

        val cp = card()
        preview = ImageView(this)
        preview.visibility = View.GONE
        preview.adjustViewBounds = true
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        cp.addView(preview, topParams())
        root.addView(cp)

        val cc = card()
        val genBtn = flatBtn("🎨 GENERATE", R.drawable.bg_gold_btn, "#0A0A00")
        cc.addView(genBtn)
        genBtn.setOnClickListener { generate() }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        cc.addView(row, topParams())
        val saveBtn = flatBtn("💾 SAVE", R.drawable.bg_gold_btn, "#0A0A00")
        val shareBtn = flatBtn("SHARE", R.drawable.bg_chip_selected, "#F2F5FF")
        val bp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        bp.setMargins(dp(4), 0, dp(4), 0)
        row.addView(saveBtn, bp)
        row.addView(shareBtn, bp)
        saveBtn.setOnClickListener { savePortrait() }
        shareBtn.setOnClickListener { sharePortrait() }
        val nc = note("GENERATE နှိပ်တိုင်း မတူတဲ့ portrait အသစ် ဆွဲပေးမယ် - နှစ်သက်တာ ရသွားအထိ နှိပ်ကြည့်ပါ။")
        nc.setPadding(0, dp(8), 0, 0)
        cc.addView(nc)
        root.addView(cc)

        val pad = TextView(this)
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, pp)
    }

    private fun generate() {
        if (busy) {
            Toast.makeText(this, "ဆွဲနေဆဲ - ခဏစောင့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        progressBar.visibility = View.VISIBLE
        statusText.text = "AI portrait ဆွဲနေသည်... (၅-၁၅ စက္ကန့်)"
        val desc = descInput.text.toString().trim()
        val base = if (desc.isNotEmpty()) desc else "beautiful portrait, elegant, studio light"
        val prompt = java.net.URLEncoder.encode(base + styleIn[styleIdx], "UTF-8")
        val url = "https://image.pollinations.ai/prompt/" + prompt + "?width=768&height=1024&nologo=true&seed=" + (0..999999).random()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .build()
                val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP " + resp.code)
                    resp.body!!.bytes()
                }
                var bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("AI ပုံ decode မရပါ")
                if (bm.width > 1080) {
                    val s = 1080f / bm.width
                    val sc = Bitmap.createScaledBitmap(bm, 1080, (bm.height * s).toInt(), true)
                    bm.recycle()
                    bm = sc
                }
                withContext(Dispatchers.Main) {
                    bmp = bm
                    preview.visibility = View.VISIBLE
                    preview.setImageBitmap(bm)
                    statusText.text = "Portrait အဆင့်သင့် ✓ SAVE / SHARE လုပ်လို့ရပါပြီ"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "AI ပုံမရပါ (rate limit ဖြစ်နိုင်): " + (e.message ?: "")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    busy = false
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun savePortrait() {
        val b = bmp
        if (b == null) {
            Toast.makeText(this, "အရင်ဆုံ GENERATE နှိပ်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val f = File(cacheDir, "portrait_out.jpg")
                if (f.exists()) f.delete()
                FileOutputStream(f).use { o -> b.compress(Bitmap.CompressFormat.JPEG, 92, o) }
                DownloadsUtil.saveFile(this@PortraitActivity, f, "PORTRAIT_" + System.currentTimeMillis() + ".jpg", "image/jpeg")
            }
            if (ok) {
                statusText.text = "ပြီးပါပြီ - Downloads မှာ သိမ်းပြီးပါပြီ"
                Toast.makeText(this@PortraitActivity, "Downloads ထဲမှာ သိမ်းပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "သိမ်းမှု မအောင်မြင်ပါ"
            }
        }
    }

    private fun sharePortrait() {
        val b = bmp
        if (b == null) {
            Toast.makeText(this, "အရင်ဆုံ GENERATE နှိပ်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    val out = File(cacheDir, "portrait_share.jpg")
                    FileOutputStream(out).use { o -> b.compress(Bitmap.CompressFormat.JPEG, 92, o) }
                    out
                }
                val uri: Uri = FileProvider.getUriForFile(this@PortraitActivity, packageName + ".fileprovider", f)
                val send = Intent(Intent.ACTION_SEND)
                send.type = "image/jpeg"
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(send, "Portrait မျှဝေမည်"))
            } catch (e: Exception) {
                Toast.makeText(this@PortraitActivity, "Share မအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
