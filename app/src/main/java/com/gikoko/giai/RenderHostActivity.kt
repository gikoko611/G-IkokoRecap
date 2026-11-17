package com.gikoko.giai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Near-invisible host: renders AI-written Three.js scenes frame by frame,
 * encodes mp4 on-device, saves to Downloads. No tool UI on the chat page.
 */
class RenderHostActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val htmlPath = intent.getStringExtra("html") ?: run { finish(); return }
        val w = Prefs.renderW - (Prefs.renderW % 2)
        val h = Prefs.renderH - (Prefs.renderH % 2)
        val frames = Prefs.renderFrames.coerceIn(8, 600)
        val fps = Prefs.renderFps.coerceIn(5, 60)

        val root = FrameLayout(this)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        web.setBackgroundColor(Neon.BG)
        web.layoutParams = FrameLayout.LayoutParams(w, h)
        root.addView(web)

        val cover = TextView(this)
        cover.setBackgroundColor(Neon.BG)
        cover.setTextColor(Neon.GREEN)
        cover.textSize = 14f
        cover.gravity = Gravity.CENTER
        root.addView(cover, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        val html = File(htmlPath).readText()
        val framesDir = File(cacheDir, "render/f" + System.currentTimeMillis())
        framesDir.mkdirs()

        lifecycleScope.launch {
            try {
                JobBus.post(JobBus.Event("status", "🎬 3D RENDER START", progress = 0))
                cover.text = "🎬 3D RENDER\n0%"
                web.loadDataWithBaseURL("https://giai.local/", html, "text/html", "utf-8", null)
                var total = -1
                var waited = 0
                while (total < 0 && waited < 30000) {
                    delay(300); waited += 300
                    val r = js(web, "typeof window.giFrame==='function' ? (window.giTotal||" + frames + ") : -1")
                    total = r.trim().removeSuffix("\"").toIntOrNull() ?: -1
                }
                if (total < 0) throw Exception("AI scene code has no giFrame() - prompt ကို ပြန်တောင်းပါ")
                val n = total.coerceIn(8, 600)
                web.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                web.layout(0, 0, w, h)
                val saved = ArrayList<File>()
                for (i in 0 until n) {
                    js(web, "giFrame(" + (i.toFloat() / (n - 1).coerceAtLeast(1)) + ")")
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    web.draw(Canvas(bmp))
                    val f = File(framesDir, "f_" + String.format("%04d", i) + ".png")
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    saved.add(f)
                    if (i % 4 == 0) {
                        val p = i * 90 / n
                        JobBus.post(JobBus.Event("status", "🎬 3D RENDER", progress = p))
                        cover.text = "🎬 3D RENDER\n" + p + "%"
                    }
                }
                cover.text = "🎞 ENCODING MP4..."
                JobBus.post(JobBus.Event("status", "🎞 ENCODING MP4", progress = 95))
                val out = File(cacheDir, "render/GI_3D_" + System.currentTimeMillis() + ".mp4")
                val ok = withContext(Dispatchers.IO) { FrameEncoder.encodePngs(saved, fps, out) }
                if (!ok) throw Exception("encode failed")
                withContext(Dispatchers.IO) {
                    val zip = File(cacheDir, "GI_3D_frames_" + System.currentTimeMillis() + ".zip")
                    DownloadsUtil.zipDir(framesDir, zip)
                    DownloadsUtil.saveFrom(this@RenderHostActivity, out, out.name)
                    DownloadsUtil.saveFrom(this@RenderHostActivity, zip, zip.name)
                    DownloadsUtil.saveFrom(this@RenderHostActivity, File(htmlPath), File(htmlPath).name)
                }
                JobBus.post(JobBus.Event("render_done", "✅ 3D VIDEO READY", path = out.absolutePath))
            } catch (e: Exception) {
                JobBus.post(JobBus.Event("render_error", "⚠ 3D RENDER FAILED: " + e.message))
            } finally {
                web.destroy()
                finish()
            }
        }
    }

    private suspend fun js(web: WebView, code: String): String =
        suspendCancellableCoroutine { cont ->
            web.evaluateJavascript(code) { v -> if (cont.isActive) cont.resume(v ?: "") }
        }
}
