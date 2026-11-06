package com.gikoko.recap

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Browser for public drama pages; does not bypass DRM or protected streams. */
class DramaBrowserActivity : AppCompatActivity() {

    private lateinit var address: EditText
    private lateinit var browser: WebView
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, gold: Boolean = false): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(if (gold) Color.BLACK else Color.WHITE)
            setBackgroundColor(if (gold) Color.rgb(220, 177, 75) else Color.rgb(30, 43, 67))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 12, 25)
        window.navigationBarColor = Color.rgb(7, 12, 25)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 12, 25))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val title = TextView(this).apply {
            text = "G.I KO KO  •  DRAMA BROWSER"
            textSize = 17f
            setTextColor(Color.rgb(230, 190, 90))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(title)

        address = EditText(this).apply {
            hint = "Paste a drama webpage or video URL"
            setSingleLine(true)
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(22, 31, 50))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setText(intent.getStringExtra("page_url").orEmpty())
        }
        root.addView(address, LinearLayout.LayoutParams(-1, dp(48)))

        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val go = button("GO")
        val searchCn = button("Chinese")
        val searchKr = button("Korean")
        navRow.addView(go, LinearLayout.LayoutParams(0, dp(44), 1f))
        navRow.addView(searchCn, LinearLayout.LayoutParams(0, dp(44), 1f))
        navRow.addView(searchKr, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(navRow)

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val copy = button("COPY PAGE LINK")
        importButton = button("IMPORT VIDEO", true)
        actionRow.addView(copy, LinearLayout.LayoutParams(0, dp(46), 1f))
        actionRow.addView(importButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(actionRow)

        status = TextView(this).apply {
            text = "Browse drama pages. Tap a video to play it, then choose IMPORT VIDEO."
            textSize = 11f
            setTextColor(Color.rgb(151, 164, 190))
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        root.addView(status)

        browser = WebView(this)
        browser.setBackgroundColor(Color.BLACK)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.mediaPlaybackRequiresUserGesture = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.settings.setSupportMultipleWindows(false)
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!url.isNullOrBlank()) address.setText(url)
                status.text = "Loading webpage..."
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!url.isNullOrBlank()) address.setText(url)
                status.text = "Page ready • Tap a video to play • IMPORT VIDEO supports public direct video files"
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        go.setOnClickListener { loadAddress() }
        address.setOnEditorActionListener { _, _, _ -> loadAddress(); true }
        searchCn.setOnClickListener {
            address.setText("https://www.google.com/search?q=Chinese+short+drama+official+full+episodes")
            loadAddress()
        }
        searchKr.setOnClickListener {
            address.setText("https://www.google.com/search?q=Korean+short+drama+official+full+episodes")
            loadAddress()
        }
        copy.setOnClickListener {
            val url = browser.url ?: address.text.toString().trim()
            if (url.isNotBlank()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Drama page URL", url))
                Toast.makeText(this, "Page link copied", Toast.LENGTH_SHORT).show()
            }
        }
        importButton.setOnClickListener { findAndImportVideo() }

        if (address.text.isNotBlank()) loadAddress() else {
            address.setText("https://www.google.com/search?q=Chinese+Korean+short+drama")
            loadAddress()
        }
    }

    private fun loadAddress() {
        var raw = address.text.toString().trim()
        if (raw.isEmpty()) return
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = "https://www.google.com/search?q=" + Uri.encode(raw)
        }
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            Toast.makeText(this, "Only public HTTP/HTTPS webpages are supported", Toast.LENGTH_LONG).show()
            return
        }
        address.setText(raw)
        browser.loadUrl(raw)
    }

    private fun findAndImportVideo() {
        val page = browser.url.orEmpty()
        val direct = page.substringBefore("?").lowercase()
        val videoExt = listOf(".mp4", ".m4v", ".webm", ".mov", ".3gp")
        if (videoExt.any { direct.endsWith(it) }) {
            downloadVideo(page)
            return
        }
        val js = """
            (function() {
              var urls = [];
              document.querySelectorAll('video').forEach(function(v) {
                if (v.currentSrc) urls.push(v.currentSrc);
                if (v.src) urls.push(v.src);
                v.querySelectorAll('source').forEach(function(s) { if (s.src) urls.push(s.src); });
              });
              document.querySelectorAll('a[href]').forEach(function(a) {
                var h=a.href||'';
                if (/\.(mp4|m4v|webm|mov|3gp)(\?|$)/i.test(h)) urls.push(h);
              });
              urls = Array.from(new Set(urls)).filter(function(u) { return /^https?:\/\//i.test(u); });
              return btoa(unescape(encodeURIComponent(JSON.stringify(urls))));
            })();
        """.trimIndent()
        browser.evaluateJavascript(js) { raw ->
            try {
                val b64 = raw?.trim()?.removeSurrounding("\"") ?: ""
                val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                val arr = org.json.JSONArray(decoded)
                val urls = (0 until arr.length()).map { arr.getString(it) }.distinct()
                if (urls.isEmpty()) {
                    status.text = "No public direct video source found. This site may use DRM, protected streaming, or a blob/MSE player. Use a downloaded video file instead."
                    Toast.makeText(this, "Direct video source မတွေ့ပါ", Toast.LENGTH_LONG).show()
                } else if (urls.size == 1) {
                    downloadVideo(urls[0])
                } else {
                    val labels = urls.mapIndexed { index, u -> "Video " + (index + 1) + " • " + u.takeLast(55) }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Choose video to import")
                        .setItems(labels) { _, which -> downloadVideo(urls[which]) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                status.text = "Could not read the page's video source. Use local file import."
            }
        }
    }

    private fun downloadVideo(videoUrl: String) {
        val uri = runCatching { Uri.parse(videoUrl) }.getOrNull()
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            Toast.makeText(this, "Unsupported video URL", Toast.LENGTH_LONG).show()
            return
        }
        importButton.isEnabled = false
        status.text = "Downloading public video file..."
        val userAgent = browser.settings.userAgentString
        val referer = browser.url
        val cookie = CookieManager.getInstance().getCookie(videoUrl)
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val requestBuilder = Request.Builder()
                        .url(videoUrl)
                        .header("User-Agent", userAgent)
                        .header("Accept", "video/*,*/*;q=0.8")
                    if (!referer.isNullOrBlank()) requestBuilder.header("Referer", referer)
                    if (!cookie.isNullOrBlank()) requestBuilder.header("Cookie", cookie)
                    http.newCall(requestBuilder.get().build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Video download HTTP " + response.code)
                        val body = response.body ?: throw IOException("Empty video response")
                        val type = body.contentType()?.toString().orEmpty()
                        val path = uri.lastPathSegment.orEmpty().substringBefore("?")
                        val ext = when {
                            path.endsWith(".webm", true) -> ".webm"
                            path.endsWith(".mov", true) -> ".mov"
                            path.endsWith(".3gp", true) -> ".3gp"
                            path.endsWith(".m4v", true) -> ".m4v"
                            path.endsWith(".mp4", true) -> ".mp4"
                            type.contains("webm", true) -> ".webm"
                            type.contains("quicktime", true) -> ".mov"
                            type.contains("3gpp", true) -> ".3gp"
                            type.contains("video", true) -> ".mp4"
                            else -> throw IOException("URL did not return a video file (content-type: $type)")
                        }
                        if (body.contentLength() > 350L * 1024L * 1024L) throw IOException("Video is over 350 MB; choose a shorter/lower-resolution clip")
                        val dir = File(cacheDir, "drama_import").apply { mkdirs() }
                        val out = File(dir, "drama_" + System.currentTimeMillis() + ext)
                        body.byteStream().use { input ->
                            out.outputStream().buffered().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    if (total > 350L * 1024L * 1024L) throw IOException("Video exceeded 350 MB limit")
                                    output.write(buffer, 0, n)
                                }
                            }
                        }
                        if (out.length() < 1024L) throw IOException("Downloaded file is too small or not a video")
                        out
                    }
                }
                setResult(Activity.RESULT_OK, Intent()
                    .putExtra("video_path", file.absolutePath)
                    .putExtra("video_title", file.name))
                Toast.makeText(this@DramaBrowserActivity, "Video imported ✓", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                status.text = "Import failed: " + (e.message ?: "Unknown error") + " • Try browser download then Video File import."
                importButton.isEnabled = true
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::browser.isInitialized) {
            browser.stopLoading()
            browser.loadUrl("about:blank")
            browser.destroy()
        }
        super.onDestroy()
    }
}
