package com.gikoko.recap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.gikoko.recap.recap.DownloadsUtil
import com.gikoko.recap.recap.FrameRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ThumbActivity : AppCompatActivity() {

    private val themes = listOf("gold", "blue", "purple", "green", "red")
    private val timesPct = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 0.95f)

    private var srcFile: File? = null
    private var frameBmp: Bitmap? = null
    private var thumbBmp: Bitmap? = null
    private var durMs = 0L
    private var timeIdx = 2
    private var themeIdx = 0
    private var posIdx = 1

    private lateinit var preview: ImageView
    private lateinit var statusText: TextView
    private lateinit var pickBtn: Button
    private lateinit var titleInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open thumbnail maker")
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

    private fun chipOf(text: String): com.google.android.material.chip.Chip {
        val c = com.google.android.material.chip.Chip(this)
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

    private fun inputStyle(e: EditText) {
        e.setBackgroundResource(R.drawable.bg_input)
        e.setTextColor(Color.parseColor("#F2F5FF"))
        e.setHintTextColor(Color.parseColor("#5A6480"))
        e.textSize = 14f
        e.setSingleLine(true)
        e.setPadding(dp(12), dp(12), dp(12), dp(12))
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
        ht.text = "THUMBNAIL MAKER"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val c1 = card()
        c1.addView(sectionTitle("① VIDEO ရွေးမည်"))
        pickBtn = flatBtn("📁 PICK VIDEO", R.drawable.bg_chip_selected, "#F2F5FF")
        c1.addView(pickBtn)
        pickBtn.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("video/*"), 99)
        }
        c1.addView(sectionTitle("FRAME TIME (video ရှည်တဲ့နေရာ)"))
        val tg = com.google.android.material.chip.ChipGroup(this)
        tg.isSingleSelection = true
        tg.isSelectionRequired = true
        listOf("START", "25%", "50%", "75%", "END").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 71000 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    timeIdx = i
                    if (srcFile != null) extractFrame()
                }
            }
            tg.addView(ch)
        }
        tg.check(71000 + timeIdx)
        c1.addView(tg)
        preview = ImageView(this)
        preview.visibility = View.GONE
        preview.adjustViewBounds = true
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        val pp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pp.topMargin = dp(10)
        c1.addView(preview, pp)
        root.addView(c1)

        val c2 = card()
        c2.addView(sectionTitle("② TITLE (ခေါင်းစဉ်)"))
        titleInput = EditText(this)
        titleInput.hint = "Thumbnail ခေါင်းစဉ်ရိုက်ပါ"
        inputStyle(titleInput)
        c2.addView(titleInput)
        val aiBtn = flatBtn("🎨 AI BACKGROUND (KEY မလို)", R.drawable.bg_chip_selected, "#F2F5FF")
        val aip = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        aip.topMargin = dp(10)
        c2.addView(aiBtn, aip)
        aiBtn.setOnClickListener { genAiBackground() }
        c2.addView(sectionTitle("③ THEME"))
        val thG = com.google.android.material.chip.ChipGroup(this)
        thG.isSingleSelection = true
        thG.isSelectionRequired = true
        listOf("GOLD", "BLUE", "PURPLE", "GREEN", "RED").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 71100 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    themeIdx = i
                    rerender()
                }
            }
            thG.addView(ch)
        }
        thG.check(71100 + themeIdx)
        c2.addView(thG)
        c2.addView(sectionTitle("④ TEXT POSITION"))
        val pg = com.google.android.material.chip.ChipGroup(this)
        pg.isSingleSelection = true
        pg.isSelectionRequired = true
        listOf("TOP", "BOTTOM").forEachIndexed { i, s ->
            val ch = chipOf(s)
            ch.id = 71200 + i
            ch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    posIdx = i
                    rerender()
                }
            }
            pg.addView(ch)
        }
        pg.check(71200 + posIdx)
        c2.addView(pg)
        val noteT = note("Video ကနေ မန်ချင်းပုံ (frame) ယူပြီး ခေါင်းစဉ် banner ထပ်ဆွဲပေးတာပါ။ ဒါမှမဟုတ် 🎨 AI BACKGROUND နှိပ်ရင် ခေါင်းစဉ်နဲ့ကိုက်တဲ့ AI ပုံ ကိုယ်တိုင်ဆွဲပေးပါမယ် (API key မလို)။ Theme ၅ မျိုး၊ အပေါ်/အောက် ရွေးလို့ရပါတယ်။ YouTube / TikTok / Facebook post တွေအတွက် သုံးပါ။")
        noteT.setPadding(0, dp(8), 0, 0)
        c2.addView(noteT)
        root.addView(c2)

        val c3 = card()
        val genBtn = flatBtn("🎨 GENERATE PREVIEW", R.drawable.bg_gold_btn, "#0A0A00")
        c3.addView(genBtn)
        genBtn.setOnClickListener { rerender() }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rp.topMargin = dp(8)
        c3.addView(row, rp)
        val saveBtn = flatBtn("💾 SAVE JPG", R.drawable.bg_gold_btn, "#0A0A00")
        val shareBtn = flatBtn("SHARE", R.drawable.bg_chip_selected, "#F2F5FF")
        val bp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        bp.setMargins(dp(4), 0, dp(4), 0)
        row.addView(saveBtn, bp)
        row.addView(shareBtn, bp)
        saveBtn.setOnClickListener { saveThumb() }
        shareBtn.setOnClickListener { shareThumb() }
        statusText = TextView(this)
        statusText.setTextColor(Color.parseColor("#3DE8F8"))
        statusText.textSize = 12f
        statusText.setPadding(0, dp(8), 0, 0)
        c3.addView(statusText)
        root.addView(c3)

        val pad = TextView(this)
        val padp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        root.addView(pad, padp)
    }

    private fun extractFrame() {
        val src = srcFile ?: return
        statusText.text = "ပုံဆွဲနေသည်..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(src.absolutePath)
                durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                var bm = mmr.getFrameAtTime((durMs * timesPct[timeIdx]).toLong() * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                mmr.release()
                if (bm == null) throw IllegalStateException("frame မရနိုင်ပါ")
                if (bm.width > 1920) {
                    val s = 1920f / bm.width
                    val sc = Bitmap.createScaledBitmap(bm, 1920, (bm.height * s).toInt(), true)
                    bm.recycle()
                    bm = sc
                }
                val t = renderThumb(bm)
                withContext(Dispatchers.Main) {
                    frameBmp = bm
                    thumbBmp = t
                    preview.visibility = View.VISIBLE
                    preview.setImageBitmap(t)
                    statusText.text = "Thumbnail အဆင့်သင့် ✓ SAVE / SHARE လုပ်လို့ရပါပြီ"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "မအောင်မြင်: " + (e.message ?: "")
                }
            }
        }
    }

    private fun genAiBackground() {
        val title = if (::titleInput.isInitialized) titleInput.text.toString().trim() else ""
        val base = if (title.isNotEmpty()) title else "cinematic recap background, dark neon city, golden light"
        val prompt = java.net.URLEncoder.encode(base + ", cinematic, dark background, neon glow, high quality", "UTF-8")
        val url = "https://image.pollinations.ai/prompt/" + prompt + "?width=1280&height=720&nologo=true&seed=" + (0..999999).random()
        statusText.text = "AI နောက်ခံပုံဆွဲနေသည်... (၅-၁၀ စက္ကန့်)"
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
                var bm = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("AI ပုံ decode မရပါ")
                if (bm.width > 1920) {
                    val s = 1920f / bm.width
                    val sc = Bitmap.createScaledBitmap(bm, 1920, (bm.height * s).toInt(), true)
                    bm.recycle()
                    bm = sc
                }
                val t = renderThumb(bm)
                withContext(Dispatchers.Main) {
                    frameBmp = bm
                    thumbBmp = t
                    preview.visibility = View.VISIBLE
                    preview.setImageBitmap(t)
                    statusText.text = "AI background အဆင့်သင့် ✓ SAVE / SHARE လုပ်လို့ရပါပြီ"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "AI ပုံမရပါ (rate limit ဖြစ်နိုင်): " + (e.message ?: "")
                }
            }
        }
    }

    private fun rerender() {
        val f = frameBmp
        if (f == null) {
            if (::statusText.isInitialized) statusText.text = "PICK VIDEO နှိပ်ပြီး video ရွေးပါ"
            return
        }
        statusText.text = "ပုံဆွဲနေသည်..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val t = renderThumb(f)
                withContext(Dispatchers.Main) {
                    thumbBmp = t
                    preview.setImageBitmap(t)
                    statusText.text = "Thumbnail အဆင့်သင့် ✓ SAVE / SHARE လုပ်လို့ရပါပြီ"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "မအောင်မြင်: " + (e.message ?: "")
                }
            }
        }
    }

    private fun renderThumb(src: Bitmap): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        val W = bmp.width
        val H = bmp.height
        val th = FrameRenderer.themeColors(themes[themeIdx])
        val title = if (::titleInput.isInitialized) titleInput.text.toString().trim() else ""
        val bannerH = H * 0.26f
        val top = if (posIdx == 0) 14f else (H - 14f - bannerH)
        val rect = RectF(14f, top, (W - 14).toFloat(), top + bannerH)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = Color.parseColor("#E60A0D16")
        c.drawRoundRect(rect, 26f, 26f, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        stroke.color = th.accent
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 5f
        c.drawRoundRect(rect, 26f, 26f, stroke)

        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tag.color = th.accent
        tag.textSize = 28f
        tag.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        c.drawText("GIKOKO RECAP", 40f, top + 44f, tag)

        if (title.isNotEmpty()) {
            val maxW = W - 80
            val avail = bannerH - 70f
            var size = 64f
            var layout: StaticLayout? = null
            while (size >= 26f) {
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
                tp.color = Color.WHITE
                tp.textSize = size
                tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val l = StaticLayout.Builder.obtain(title, 0, title.length, tp, maxW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(6f, 1.0f)
                    .build()
                layout = l
                if (l.height <= avail) break
                size -= 4f
            }
            val l = layout
            if (l != null) {
                c.save()
                c.translate(40f, top + 58f)
                l.draw(c)
                c.restore()
            }
        }
        return bmp
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 99 && resultCode == -1 && data?.data != null) {
            runCatching {
                val dst = File(cacheDir, "thumb_src.mp4")
                if (dst.exists()) dst.delete()
                contentResolver.openInputStream(data.data!!)?.use { ins ->
                    dst.outputStream().use { ins.copyTo(it) }
                }
                if (dst.length() > 0L) {
                    srcFile = dst
                    extractFrame()
                }
            }.onFailure {
                Toast.makeText(this, "Video ဖွင့်မရပါ: " + (it.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveThumb() {
        val t = thumbBmp
        if (t == null) {
            Toast.makeText(this, "Video ရွေးပြီး GENERATE အရင်လုပ်ပါ", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val f = File(cacheDir, "thumb_out.jpg")
                if (f.exists()) f.delete()
                FileOutputStream(f).use { t.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                DownloadsUtil.saveFile(this@ThumbActivity, f, "GIkokoThumb_" + System.currentTimeMillis() + ".jpg", "image/jpeg")
            }
            if (ok) {
                statusText.text = "ပြီးပါပြီ ✓ - Downloads ထဲမှာ သိမ်းပြီးပါပြီ"
                Toast.makeText(this@ThumbActivity, "Downloads ထဲမှာ သိမ်းပြီးပါပြီ", Toast.LENGTH_LONG).show()
            } else {
                statusText.text = "သိမ်းမှု မအောင်မြင်ပါ"
            }
        }
    }

    private fun shareThumb() {
        val t = thumbBmp
        if (t == null) {
            Toast.makeText(this, "Video ရွေးပြီး GENERATE အရင်လုပ်ပါ", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    val out = File(cacheDir, "thumb_share.jpg")
                    FileOutputStream(out).use { t.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    out
                }
                val uri = FileProvider.getUriForFile(this@ThumbActivity, packageName + ".fileprovider", f)
                val send = Intent(Intent.ACTION_SEND)
                send.type = "image/jpeg"
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(send, "Thumbnail မျှဝေမည်"))
            } catch (e: Exception) {
                Toast.makeText(this@ThumbActivity, "Share failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
