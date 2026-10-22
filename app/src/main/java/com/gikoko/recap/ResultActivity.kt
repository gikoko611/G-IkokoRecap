package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gikoko.recap.recap.DownloadsUtil
import java.io.File

class ResultActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var videoFile: File
    private var srtFile: File? = null
    private var summaryText: String = ""
    private var thumbFile: File? = null
    private var shortFile: File? = null
    private var podFile: File? = null
    private var sourceUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, "open result")
        setContentView(R.layout.activity_result)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        val vPath = intent.getStringExtra("video") ?: ""
        val sPath = intent.getStringExtra("srt") ?: ""
        val title = intent.getStringExtra("title") ?: "Recap"
        summaryText = intent.getStringExtra("summary") ?: ""
        val tPath = intent.getStringExtra("thumb") ?: ""
        if (vPath.isEmpty() || !File(vPath).exists()) {
            Toast.makeText(this, L.s(this, "Video မတွေ့ပါ", "Video not found"), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        videoFile = File(vPath)
        srtFile = if (sPath.isNotEmpty() && File(sPath).exists()) File(sPath) else null
        thumbFile = if (tPath.isNotEmpty() && File(tPath).exists()) File(tPath) else null
        val shPath = intent.getStringExtra("short") ?: ""
        val podPath = intent.getStringExtra("podcast") ?: ""
        shortFile = if (shPath.isNotEmpty() && File(shPath).exists()) File(shPath) else null
        podFile = if (podPath.isNotEmpty() && File(podPath).exists()) File(podPath) else null
        sourceUrl = intent.getStringExtra("source_url") ?: ""

        val head = TextView(this)
        head.text = title
        head.setTextColor(Color.parseColor("#FFC940"))
        head.textSize = 18f
        head.gravity = Gravity.CENTER
        head.setTypeface(null, Typeface.BOLD)
        head.setPadding(dp(16), dp(16), dp(16), dp(6))
        root.addView(head)

        val ok = TextView(this)
        ok.text = L.s(this, "Recap အဆင့်သင့်ပါပြီ", "Your recap is ready")
        ok.setTextColor(Color.parseColor("#3DE8F8"))
        ok.textSize = 12f
        ok.gravity = Gravity.CENTER
        ok.setPadding(0, 0, 0, dp(10))
        root.addView(ok)

        val pv = PlayerView(this)
        root.addView(pv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        player = ExoPlayer.Builder(this).build()
        pv.player = player
        player?.setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
        player?.prepare()
        player?.playWhenReady = true

        if (summaryText.isNotEmpty()) {
            val sum = TextView(this)
            sum.text = summaryText
            sum.setTextColor(Color.parseColor("#C9D2EC"))
            sum.textSize = 13f
            sum.maxLines = 5
            sum.ellipsize = TextUtils.TruncateAt.END
            sum.setPadding(dp(12), dp(10), dp(12), dp(4))
            root.addView(sum)
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rp.setMargins(dp(12), dp(12), dp(12), dp(4))
        root.addView(row, rp)

        val save = btn(L.s(this, "သိမ်းမည်", "Save"), R.drawable.bg_gold_btn, "#0A0A00")
        val share = btn("Share Video", R.drawable.bg_chip_selected, "#F2F5FF")
        val again = btn(L.s(this, "အသစ်လုပ်မည်", "New recap"), R.drawable.bg_mode_card, "#C9D2EC")
        row.addView(save, btnParams())
        row.addView(share, btnParams())
        row.addView(again, btnParams())

        val srt = srtFile
        if (srt != null || summaryText.isNotEmpty()) {
            val row2 = LinearLayout(this)
            row2.orientation = LinearLayout.HORIZONTAL
            val rp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rp2.setMargins(dp(12), dp(4), dp(12), dp(16))
            root.addView(row2, rp2)
            if (srt != null) {
                val shSrt = btn("Share SRT", R.drawable.bg_mode_card, "#C9D2EC")
                row2.addView(shSrt, btnParams())
                shSrt.setOnClickListener { shareSrt() }
            }
            if (summaryText.isNotEmpty()) {
                val shTxt = btn("Share Text", R.drawable.bg_mode_card, "#C9D2EC")
                row2.addView(shTxt, btnParams())
                shTxt.setOnClickListener { shareText() }
            }
        }

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        val rp3 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rp3.setMargins(dp(12), dp(4), dp(12), dp(16))
        root.addView(row3, rp3)
        val tk = btn("TikTok", R.drawable.bg_chip_selected, "#F2F5FF")
        val fb = btn("Facebook", R.drawable.bg_chip_selected, "#F2F5FF")
        val ms = btn("Messenger", R.drawable.bg_chip_selected, "#F2F5FF")
        row3.addView(tk, btnParams())
        row3.addView(fb, btnParams())
        row3.addView(ms, btnParams())
        tk.setOnClickListener { shareToApp("com.zhiliaoapp.musically") }
        fb.setOnClickListener { shareToApp("com.facebook.katana") }
        ms.setOnClickListener { shareToApp("com.facebook.orca") }
        if (thumbFile != null) {
            val th = btn("Thumbnail", R.drawable.bg_mode_card, "#C9D2EC")
            row3.addView(th, btnParams())
            th.setOnClickListener { shareThumb() }
        }

        val row4 = LinearLayout(this)
        row4.orientation = LinearLayout.HORIZONTAL
        val rp4 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rp4.setMargins(dp(12), dp(4), dp(12), dp(16))
        root.addView(row4, rp4)
        if (shortFile != null) {
            val sh = btn("SHARE SHORTS", R.drawable.bg_chip_selected, "#F2F5FF")
            row4.addView(sh, btnParams())
            sh.setOnClickListener { shareShorts() }
        }
        if (podFile != null) {
            val pd = btn("SAVE PODCAST", R.drawable.bg_gold_btn, "#0A0A00")
            row4.addView(pd, btnParams())
            pd.setOnClickListener { savePodcast() }
        }
        if (sourceUrl.isNotEmpty() && summaryText.contains("CHAPTERS")) {
            val cl = btn("CHAPTER LINKS", R.drawable.bg_mode_card, "#C9D2EC")
            row4.addView(cl, btnParams())
            cl.setOnClickListener { shareChapterLinks() }
        }

        save.setOnClickListener { saveRecap() }
        share.setOnClickListener { shareRecap() }
        again.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
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

    private fun saveRecap() {
        val srt = srtFile
        val okSrt = if (srt != null && srt.exists()) srt else null
        val ok = DownloadsUtil.saveRecap(this, videoFile, okSrt)
        if (ok) {
            Toast.makeText(this, L.s(this, "Downloads ထဲမှာ သိမ်းပြီးပါပြီ", "Saved to Downloads"), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, L.s(this, "သိမ်းမအောင်မြင်ပါ", "Save failed"), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareRecap() {
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", videoFile)
            val send = Intent(Intent.ACTION_SEND)
            send.type = "video/mp4"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.putExtra(Intent.EXTRA_TEXT, "GIkoko One Recap (AI)")
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Recap မျှဝေမည်"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareSrt() {
        val srt = srtFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", srt)
            val send = Intent(Intent.ACTION_SEND)
            send.type = "application/x-subrip"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "စာတန်းထိုး မျှဝေမည်"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareText() {
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_SUBJECT, "GIkoko One Recap")
        send.putExtra(Intent.EXTRA_TEXT, summaryText)
        startActivity(Intent.createChooser(send, "Recap စာသား မျှဝေမည်"))
    }

    private fun shareToApp(pkg: String) {
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", videoFile)
            val send = Intent(Intent.ACTION_SEND)
            send.type = "video/mp4"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.putExtra(Intent.EXTRA_TEXT, "GIkoko One Recap (AI)")
            send.setPackage(pkg)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(send)
        } catch (e: Exception) {
            Toast.makeText(this, L.s(this, "App မတွေ့ပါ - share chooser ပွင့်မည်", "App not found - share chooser will open"), Toast.LENGTH_SHORT).show()
            shareRecap()
        }
    }

    private fun shareThumb() {
        val t = thumbFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", t)
            val send = Intent(Intent.ACTION_SEND)
            send.type = "image/jpeg"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, L.s(this, "Thumbnail မျှဝေမည်", "Share thumbnail")))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareShorts() {
        val f = shortFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", f)
            val send = Intent(Intent.ACTION_SEND)
            send.type = "video/mp4"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.putExtra(Intent.EXTRA_TEXT, "GIKOKO RECAP (AI)")
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, L.s(this, "Shorts မျှဝေမည်", "Share shorts")))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun savePodcast() {
        val f = podFile ?: return
        val ok = DownloadsUtil.saveRecap(this, f, null)
        if (ok) {
            Toast.makeText(this, L.s(this, "Downloads ထဲမှာ သိမ်းပြီးပါပြီ", "Saved to Downloads"), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, L.s(this, "သိမ်းမအောင်မြင်ပါ", "Save failed"), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareChapterLinks() {
        val sb = StringBuilder()
        val parts = summaryText.split("CHAPTERS")
        if (parts.size > 1) {
            val lines = parts[1].split("\n")
            for (line in lines) {
                val t = line.trim()
                if (t.contains(" — ")) {
                    val mmss = t.substringBefore(" — ").trim()
                    val m = Regex("(\\d+):(\\d+)").find(mmss)
                    if (m != null) {
                        val sec = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                        val joiner = if (sourceUrl.contains("?")) "&" else "?"
                        sb.append(t.substringAfter(" — ").trim()).append("\n").append(sourceUrl).append(joiner).append("t=").append(sec).append("\n\n")
                    }
                }
            }
        }
        if (sb.isEmpty()) {
            Toast.makeText(this, "Chapters မရှိပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_SUBJECT, "GIKOKO RECAP - Chapters")
        send.putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
        startActivity(Intent.createChooser(send, L.s(this, "Chapter link မျှဝေမည်", "Share chapter links")))
    }

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
