package com.gikoko.recap

import android.content.ContentUris
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private data class GItem(val name: String, val uri: Uri?, val path: String?, val size: Long)

    private val prefixes = listOf("AIstudio_", "MERGED_", "BGM_", "TRIM_", "TEXT_", "INTRO_", "SLIDE_", "VO_")
    private val items = ArrayList<GItem>()
    private lateinit var listBox: LinearLayout
    private lateinit var countText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open gallery")
        setContentView(R.layout.activity_settings)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        build(root)
        load()
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
        ht.text = "MY GALLERY"
        ht.textSize = 18f
        ht.setTypeface(null, Typeface.BOLD)
        ht.setTextColor(Color.parseColor("#FFC940"))
        ht.letterSpacing = 0.2f
        head.addView(ht)

        val refresh = TextView(this)
        refresh.text = "⟳"
        refresh.textSize = 22f
        refresh.setTextColor(Color.parseColor("#3DE8F8"))
        refresh.setPadding(dp(16), dp(4), dp(16), dp(4))
        refresh.setOnClickListener { load() }
        head.addView(refresh)

        countText = TextView(this)
        countText.setTextColor(Color.parseColor("#8A93B2"))
        countText.textSize = 12f
        countText.setPadding(dp(16), dp(4), dp(16), dp(4))
        root.addView(countText)

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        root.addView(listBox)

        val n = TextView(this)
        n.text = "AI STUDIO / MERGER / VIDEO LAB တွေထဲက လုပ်ခဲ့တဲ့ ဖိုင်တွေ (AIstudio_, MERGED_, BGM_, TRIM_, TEXT_, INTRO_, SLIDE_, VO_ နာမည်တွေနဲ့ စတဲ့) ဒီမှာ စုပြပါမယ် - ဖွင့်ကြည့်၊ share၊ ဖျက်လို့ရပါတယ်။"
        n.setTextColor(Color.parseColor("#8A93B2"))
        n.textSize = 11f
        n.setPadding(dp(16), dp(8), dp(16), dp(16))
        root.addView(n)
    }

    private fun load() {
        countText.text = "ရှာနေသည်..."
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { scan() }
            items.clear()
            items.addAll(list)
            items.sortByDescending { it.name }
            runOnUiThread { refresh() }
        }
    }

    private fun scan(): List<GItem> {
        val out = ArrayList<GItem>()
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val sel = prefixes.joinToString(" OR ") { MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?" }
                val args = prefixes.map { it + "%" }.toTypedArray()
                contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                    sel, args, MediaStore.MediaColumns.DISPLAY_NAME + " DESC"
                )?.use { c ->
                    val iId = c.getColumnIndex(MediaStore.MediaColumns._ID)
                    val iName = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val iSize = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    while (c.moveToNext()) {
                        val name = c.getString(iName) ?: continue
                        val size = if (iSize >= 0) c.getLong(iSize) else 0L
                        out.add(GItem(name, ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(iId)), null, size))
                    }
                }
            } catch (e: Exception) {
                // fall through to file scan
            }
        }
        if (out.isEmpty()) {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val files = dir?.listFiles()
                if (files != null) {
                    for (f in files) {
                        if (prefixes.any { f.name.startsWith(it) }) {
                            out.add(GItem(f.name, null, f.absolutePath, f.length()))
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return out
    }

    private fun mimeOf(name: String): String {
        return if (name.endsWith(".mp4", true)) "video/mp4"
        else if (name.endsWith(".png", true)) "image/png"
        else if (name.endsWith(".webp", true)) "image/webp"
        else "image/jpeg"
    }

    private fun sizeText(s: Long): String {
        return if (s > 1024 * 1024) String.format("%.1f MB", s / (1024.0 * 1024.0)) else String.format("%d KB", s / 1024)
    }

    private fun refresh() {
        listBox.removeAllViews()
        countText.text = if (items.isEmpty()) "ဘာမှမရှိသေးပါ - AI STUDIO / MERGER တို့နဲ့ လုပ်ပြီးရင် ဒီမှာ ပေါ်လာမယ်" else ("ဖိုင် " + items.size + " ခု တွေ့ပါသည် (အသစ်က အပေါ်ဆုံး)")
        for (i in items.indices) {
            val gi = items[i]
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setBackgroundResource(R.drawable.bg_card)
            card.setPadding(dp(12), dp(10), dp(12), dp(10))
            val cp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cp.setMargins(dp(16), dp(6), dp(16), dp(6))
            card.layoutParams = cp

            val titleRow = LinearLayout(this)
            titleRow.orientation = LinearLayout.HORIZONTAL
            titleRow.gravity = Gravity.CENTER_VERTICAL
            card.addView(titleRow)

            val icon = if (gi.name.endsWith(".mp4", true)) "🎬" else "🖼"
            val nm = TextView(this)
            nm.text = icon + " " + gi.name
            nm.textSize = 13f
            nm.setTextColor(Color.parseColor("#F2F5FF"))
            nm.setTypeface(null, Typeface.BOLD)
            nm.setSingleLine(true)
            nm.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            val np = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            titleRow.addView(nm, np)
            val sz = TextView(this)
            sz.text = sizeText(gi.size)
            sz.textSize = 11f
            sz.setTextColor(Color.parseColor("#8A93B2"))
            titleRow.addView(sz)

            val btnRow = LinearLayout(this)
            btnRow.orientation = LinearLayout.HORIZONTAL
            val brp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            brp.topMargin = dp(6)
            card.addView(btnRow, brp)

            val openBtn = TextView(this)
            openBtn.text = "▶ OPEN"
            openBtn.textSize = 12f
            openBtn.gravity = Gravity.CENTER
            openBtn.setTextColor(Color.parseColor("#0A0A00"))
            openBtn.setTypeface(null, Typeface.BOLD)
            openBtn.setBackgroundResource(R.drawable.bg_gold_btn)
            openBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
            val obp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            obp.marginEnd = dp(4)
            btnRow.addView(openBtn, obp)
            openBtn.setOnClickListener { openItem(gi) }

            val shareBtn = TextView(this)
            shareBtn.text = "↗ SHARE"
            shareBtn.textSize = 12f
            shareBtn.gravity = Gravity.CENTER
            shareBtn.setTextColor(Color.parseColor("#F2F5FF"))
            shareBtn.setBackgroundResource(R.drawable.bg_chip_selected)
            shareBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
            val sbp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            sbp.marginEnd = dp(4)
            btnRow.addView(shareBtn, sbp)
            shareBtn.setOnClickListener { shareItem(gi) }

            val delBtn = TextView(this)
            delBtn.text = "🗑 DEL"
            delBtn.textSize = 12f
            delBtn.gravity = Gravity.CENTER
            delBtn.setTextColor(Color.parseColor("#FF5C5C"))
            delBtn.setBackgroundResource(R.drawable.bg_input)
            delBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
            val dbp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            btnRow.addView(delBtn, dbp)
            delBtn.setOnClickListener { deleteItem(gi) }

            listBox.addView(card)
        }
    }

    private fun openItem(it: GItem) {
        try {
            val uri: Uri = it.uri ?: FileProvider.getUriForFile(this, packageName + ".fileprovider", File(it.path ?: return))
            val v = Intent(Intent.ACTION_VIEW)
            v.setDataAndType(uri, mimeOf(it.name))
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(v)
        } catch (e: Exception) {
            Toast.makeText(this, "ဖွင့်မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareItem(it: GItem) {
        try {
            val uri: Uri = it.uri ?: FileProvider.getUriForFile(this, packageName + ".fileprovider", File(it.path ?: return))
            val send = Intent(Intent.ACTION_SEND)
            send.type = mimeOf(it.name)
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "မျှဝေမည်"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteItem(it: GItem) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    if (it.uri != null) {
                        contentResolver.delete(it.uri, null, null) > 0
                    } else {
                        File(it.path ?: return@withContext false).delete()
                    }
                } catch (e: Exception) {
                    false
                }
            }
            runOnUiThread {
                if (ok) Toast.makeText(this@GalleryActivity, "ဖျက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this@GalleryActivity, "ဖျက်မအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
                load()
            }
        }
    }
}
