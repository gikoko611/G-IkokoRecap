package com.gikoko.recap

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import java.io.File

class ResultActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var videoFile: File
    private lateinit var srtFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        val root = findViewById<LinearLayout>(R.id.rootContainer)
        val vPath = intent.getStringExtra("video") ?: ""
        val sPath = intent.getStringExtra("srt") ?: ""
        val title = intent.getStringExtra("title") ?: "Recap"
        if (vPath.isEmpty()) {
            Toast.makeText(this, "Video not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        videoFile = File(vPath)
        srtFile = File(sPath)

        val head = TextView(this)
        head.text = title
        head.setTextColor(Color.parseColor("#FFC940"))
        head.textSize = 18f
        head.gravity = Gravity.CENTER
        head.setTypeface(null, Typeface.BOLD)
        head.setPadding(dp(16), dp(16), dp(16), dp(6))
        root.addView(head)

        val ok = TextView(this)
        ok.text = "Recap အဆင့်သင့်ပါပြီ"
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

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rp.setMargins(dp(12), dp(12), dp(12), dp(16))
        root.addView(row, rp)

        val save = btn("သိမ်းမည်", R.drawable.bg_gold_btn, "#0A0A00")
        val share = btn("Share", R.drawable.bg_chip_selected, "#F2F5FF")
        val again = btn("အသစ်လုပ်မည်", R.drawable.bg_mode_card, "#C9D2EC")
        row.addView(save, btnParams())
        row.addView(share, btnParams())
        row.addView(again, btnParams())

        save.setOnClickListener { saveToDownloads() }
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

    private fun saveToDownloads() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = contentResolver
                val stamp = System.currentTimeMillis()
                val values = ContentValues()
                values.put(MediaStore.Downloads.DISPLAY_NAME, "GIkokoRecap_" + stamp + ".mp4")
                values.put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                values.put(MediaStore.Downloads.IS_PENDING, 1)
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Download entry မလုပ်နိုင်ပါ")
                val out = resolver.openOutputStream(uri) ?: throw IllegalStateException("Write failed")
                out.use { o -> videoFile.inputStream().use { it.copyTo(o) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                if (srtFile.exists()) {
                    val v2 = ContentValues()
                    v2.put(MediaStore.Downloads.DISPLAY_NAME, "GIkokoRecap_" + stamp + ".srt")
                    v2.put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    v2.put(MediaStore.Downloads.IS_PENDING, 1)
                    val u2 = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v2)
                    if (u2 != null) {
                        val o2 = resolver.openOutputStream(u2)
                        if (o2 != null) {
                            o2.use { oo -> srtFile.inputStream().use { it.copyTo(oo) } }
                        }
                        v2.clear()
                        v2.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(u2, v2, null, null)
                    }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                videoFile.copyTo(File(dir, videoFile.name), overwrite = true)
                if (srtFile.exists()) srtFile.copyTo(File(dir, srtFile.name), overwrite = true)
            }
            Toast.makeText(this, "Downloads ထဲမှာ သိမ်းပြီးပါပြီ", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "သိမ်းမအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
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
