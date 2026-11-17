package com.gikoko.giai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity(), JobBus.Listener {

    private val FENCE = "\u0060\u0060\u0060"

    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var attachBar: HorizontalScrollView
    private lateinit var attachRow: LinearLayout
    private lateinit var jobChip: TextView
    private lateinit var modelChip: TextView

    private val history = ArrayList<AiClient.Msg>()
    private val attachments = ArrayList<Attach>()
    private var typingView: TextView? = null

    class Attach(val name: String, val b64: String?, val text: String?, val isImage: Boolean)

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { addAttach(it) } }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { addAttach(it) } }
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { addAttach(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadHistory()
        JobBus.listener = this
        updateJobChip(JobBus.status)
    }

    override fun onDestroy() {
        if (JobBus.listener === this) JobBus.listener = null
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Neon.BG)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.setPadding(Neon.dp(this, 16), Neon.dp(this, 12), Neon.dp(this, 16), Neon.dp(this, 12))
        header.setBackgroundColor(Neon.SURFACE)
        header.gravity = Gravity.CENTER_VERTICAL
        val title = Neon.label(this, "G.I AI", 20f, Neon.GREEN, true)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(title)
        modelChip = Neon.label(this, "◈ " + Prefs.aiModel, 11f, Neon.DIM)
        modelChip.background = getDrawable(R.drawable.bg_card)
        modelChip.setPadding(Neon.dp(this, 10), Neon.dp(this, 4), Neon.dp(this, 10), Neon.dp(this, 4))
        header.addView(modelChip)
        val gear = TextView(this)
        gear.text = "⚙"
        gear.textSize = 22f
        gear.setPadding(Neon.dp(this, 12), 0, 0, 0)
        gear.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        header.addView(gear)
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        jobChip = Neon.label(this, "", 12f, Neon.GREEN)
        jobChip.background = getDrawable(R.drawable.bg_msg_user)
        jobChip.setPadding(Neon.dp(this, 12), Neon.dp(this, 6), Neon.dp(this, 12), Neon.dp(this, 6))
        jobChip.visibility = View.GONE
        val jw = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        jw.setMargins(Neon.dp(this, 16), Neon.dp(this, 8), Neon.dp(this, 16), 0)
        root.addView(jobChip, jw)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(Neon.dp(this, 12), Neon.dp(this, 12), Neon.dp(this, 12), Neon.dp(this, 12))
        scroll = ScrollView(this)
        scroll.addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(scroll)

        attachRow = LinearLayout(this)
        attachRow.orientation = LinearLayout.HORIZONTAL
        attachRow.setPadding(Neon.dp(this, 12), 0, Neon.dp(this, 12), 0)
        attachBar = HorizontalScrollView(this)
        attachBar.addView(attachRow)
        attachBar.visibility = View.GONE
        root.addView(attachBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(Neon.dp(this, 10), Neon.dp(this, 8), Neon.dp(this, 10), Neon.dp(this, 10))
        row.gravity = Gravity.BOTTOM
        val plus = TextView(this)
        plus.text = "+"
        plus.textSize = 24f
        plus.setTextColor(Neon.GREEN)
        plus.gravity = Gravity.CENTER
        plus.background = getDrawable(R.drawable.bg_send)
        val pw = Neon.dp(this, 44)
        plus.layoutParams = LinearLayout.LayoutParams(pw, pw).apply { setMargins(0, 0, Neon.dp(this, 8), Neon.dp(this, 2)) }
        plus.setOnClickListener { showAttachMenu(plus) }
        row.addView(plus)

        input = EditText(this)
        input.hint = "Message G.I AI..."
        input.setTextColor(Neon.TEXT)
        input.setHintTextColor(Neon.DIM)
        input.textSize = 14f
        input.background = getDrawable(R.drawable.bg_input)
        input.setPadding(Neon.dp(this, 14), Neon.dp(this, 12), Neon.dp(this, 14), Neon.dp(this, 12))
        input.maxLines = 5
        input.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(input)

        val send = TextView(this)
        send.text = " ➤"
        send.textSize = 20f
        send.setTextColor(Neon.GREEN)
        send.gravity = Gravity.CENTER
        send.background = getDrawable(R.drawable.bg_send)
        val sw = Neon.dp(this, 44)
        send.layoutParams = LinearLayout.LayoutParams(sw, sw).apply { setMargins(Neon.dp(this, 8), 0, 0, Neon.dp(this, 2)) }
        send.setOnClickListener { send() }
        row.addView(send)
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun showAttachMenu(anchor: View) {
        val opts = arrayOf("🖼 Image", "🎞 Video", "📄 File")
        android.app.AlertDialog.Builder(this)
            .setTitle("Upload")
            .setItems(opts) { _, w ->
                when (w) {
                    0 -> pickImage.launch("image/*")
                    1 -> pickVideo.launch("video/*")
                    else -> pickFile.launch("*/*")
                }
            }.show()
    }

    private fun addAttach(uri: Uri) {
        try {
            val cr = contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            var name = "file"
            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
            if (mime.startsWith("image/")) {
                val b64 = withImgB64(uri)
                attachments.add(Attach(name, b64, null, true))
            } else if (mime.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".md") ||
                name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".json") ||
                name.endsWith(".sh") || name.endsWith(".kt") || name.endsWith(".gradle") ||
                name.endsWith(".yml") || name.endsWith(".html") || name.endsWith(".xml")) {
                val t = cr.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }?.take(20000)
                attachments.add(Attach(name, null, t, false))
            } else {
                val size = cr.openAssetFileDescriptor(uri)?.use { it.length } ?: -1
                attachments.add(Attach(name + " (" + (size / 1024) + " KB)", null, null, false))
            }
            renderAttachBar()
        } catch (e: Exception) {
            Toast.makeText(this, "attach failed: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun withImgB64(uri: Uri): String {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) sample *= 2
        val o2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o2) }
            ?: return ""
        val bos = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
        bmp.recycle()
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun renderAttachBar() {
        attachRow.removeAllViews()
        if (attachments.isEmpty()) { attachBar.visibility = View.GONE; return }
        attachBar.visibility = View.VISIBLE
        for ((i, a) in attachments.withIndex()) {
            val chip = Neon.label(this, (if (a.isImage) "🖼 " else "📄 ") + a.name + "  ✕", 12f, Neon.GREEN)
            chip.background = getDrawable(R.drawable.bg_card)
            chip.setPadding(Neon.dp(this, 10), Neon.dp(this, 6), Neon.dp(this, 10), Neon.dp(this, 6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, Neon.dp(this, 6), Neon.dp(this, 8), 0)
            chip.layoutParams = lp
            chip.setOnClickListener { attachments.removeAt(i); renderAttachBar() }
            attachRow.addView(chip)
        }
    }

    private fun sysPrompt(): String {
        return "You are G.I AI, an on-device assistant by I KO KO. Reply in the user's language (Burmese welcome). " +
            "You have two special abilities. " +
            "1) 3D ANIMATION VIDEO: when the user asks for a 3D animation/video/scene, reply with ONE " + FENCE + "html code block containing a complete self-contained Three.js scene " +
            "(load three.js from https://unpkg.com/three@0.160.0/build/three.min.js). Contract: define window.giTotal = <total frames, 48-240> and function window.giFrame(t) where t goes 0..1 and deterministically updates the scene. No requestAnimationFrame, no Math.random without seed. The app renders frames and encodes an mp4 automatically in the background. " +
            "2) TERMUX: when the user asks to run shell/linux tools, reply with ONE " + FENCE + "termux code block containing a bash script; the app executes it inside Termux. " +
            "For normal questions answer directly and concisely."
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty() && attachments.isEmpty()) return
        val atts = ArrayList(attachments)
        attachments.clear(); renderAttachBar()
        input.setText("")
        addBubble("user", text + if (atts.isNotEmpty()) "\n📎 " + atts.joinToString(", ") { it.name } else "")
        history.add(AiClient.Msg("user", text, atts.firstOrNull { it.isImage }?.b64))
        showTyping()
        lifecycleScope.launch {
            val reply = try {
                val msgs = ArrayList<AiClient.Msg>()
                msgs.add(AiClient.Msg("system", sysPrompt()))
                synchronized(history) {
                    if (history.size > 12) msgs.addAll(history.subList(history.size - 12, history.size - 1))
                    else if (history.size > 1) msgs.addAll(history.subList(0, history.size - 1))
                }
                val cur = history.last()
                if (cur.imgB64 != null) msgs.add(AiClient.Msg("user", attachText(text, atts), cur.imgB64))
                else msgs.add(AiClient.Msg("user", attachText(text, atts)))
                withContext(Dispatchers.IO) { AiClient.chat(this@MainActivity, msgs) }
            } catch (e: Exception) {
                "⚠ ERROR: " + e.message
            }
            hideTyping()
            addBubble("ai", reply)
            history.add(AiClient.Msg("assistant", reply))
            saveHistory()
            handleAiReply(reply)
        }
    }

    private fun attachText(text: String, atts: List<Attach>): String {
        val sb = StringBuilder(text)
        for (a in atts) {
            if (a.text != null) sb.append("\n\nFILE: ").append(a.name).append("\n").append(a.text)
            else if (!a.isImage) sb.append("\n\nATTACHED: ").append(a.name)
        }
        return sb.toString()
    }

    private fun handleAiReply(reply: String) {
        val rx = Regex(FENCE + "([a-zA-Z]+)?\\n?([\\s\\S]*?)" + FENCE)
        for (m in rx.findAll(reply)) {
            val lang = (m.groupValues[1]).lowercase()
            val code = m.groupValues[2]
            when (lang) {
                "html" -> if (code.contains("giFrame")) start3D(code)
                "termux" -> runTermux(code)
            }
            addSaveCodeButton(lang, code)
        }
    }

    private fun start3D(code: String) {
        val f = File(filesDir, "render")
        f.mkdirs()
        val scene = File(f, "scene_" + System.currentTimeMillis() + ".html")
        scene.writeText(code)
        Toast.makeText(this, "🎬 3D render နောက်ကွယ်မှ စပြီ", Toast.LENGTH_SHORT).show()
        val i = Intent(this, RenderHostActivity::class.java)
        i.putExtra("html", scene.absolutePath)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun runTermux(code: String) {
        val ok = TermuxClient.run(this, code)
        Toast.makeText(this, if (ok) "▶ Termux ထဲ run နေပြီ" else "⚠ Termux မရှိ / allow-external-apps မဖွင့်ရသေး", Toast.LENGTH_LONG).show()
    }

    private fun addSaveCodeButton(lang: String, code: String) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = getDrawable(R.drawable.bg_msg_ai)
        row.setPadding(Neon.dp(this, 12), Neon.dp(this, 6), Neon.dp(this, 12), Neon.dp(this, 6))
        val b = Neon.btn(this, "💾 SAVE " + lang.uppercase() + " FILE") {
            val ext = if (lang.isEmpty()) "txt" else lang
            DownloadsUtil.saveBytes(this, code.toByteArray(), "GI_code_" + System.currentTimeMillis() + "." + ext)
            Toast.makeText(this, "⬇ Download/GIAI ထဲ သိမ်းပြီ", Toast.LENGTH_SHORT).show()
        }
        row.addView(b)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(Neon.dp(this, 24), Neon.dp(this, 4), Neon.dp(this, 24), Neon.dp(this, 8))
        list.addView(row, lp)
        scrollBottom()
    }

    private fun addBubble(role: String, text: String) {
        val v = Neon.label(this, text, 14f, if (role == "user") Neon.TEXT else Neon.GREEN)
        v.background = getDrawable(if (role == "user") R.drawable.bg_msg_user else R.drawable.bg_msg_ai)
        v.setPadding(Neon.dp(this, 12), Neon.dp(this, 10), Neon.dp(this, 12), Neon.dp(this, 10))
        v.setTextIsSelectable(true)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(if (role == "user") Neon.dp(this, 48) else Neon.dp(this, 4),
            Neon.dp(this, 4), if (role == "user") Neon.dp(this, 4) else Neon.dp(this, 48), Neon.dp(this, 4))
        list.addView(v, lp)
        scrollBottom()
    }

    private fun addResultBubble(msg: String, path: String) {
        addBubble("ai", msg)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = getDrawable(R.drawable.bg_msg_ai)
        row.setPadding(Neon.dp(this, 12), Neon.dp(this, 6), Neon.dp(this, 12), Neon.dp(this, 6))
        row.addView(Neon.btn(this, "📂 OPEN") {
            val f = File(path)
            val uri = FileProvider.getUriForFile(this, "com.gikoko.giai.files", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, DownloadsUtil.mimeOf(f.name))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { startActivity(i) } catch (e: Exception) { Toast.makeText(this, "no viewer", Toast.LENGTH_SHORT).show() }
        })
        row.addView(Neon.btn(this, "📤 SHARE") {
            val f = File(path)
            val uri = FileProvider.getUriForFile(this, "com.gikoko.giai.files", f)
            val i = Intent(Intent.ACTION_SEND)
            i.type = DownloadsUtil.mimeOf(f.name)
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Share"))
        })
        val note = Neon.label(this, "⬇ MP4 + frames ZIP + scene HTML သည် Downloads/GIAI ထဲ ရှိပြီး", 11f, Neon.DIM)
        val lpn = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpn.setMargins(Neon.dp(this, 24), 0, Neon.dp(this, 24), 0)
        list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(Neon.dp(this, 24), 2, Neon.dp(this, 24), 2) })
        list.addView(note, lpn)
        scrollBottom()
    }

    private fun showTyping() {
        val t = Neon.label(this, "● ● ● G.I AI typing", 13f, Neon.DIM)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(Neon.dp(this, 8), Neon.dp(this, 6), Neon.dp(this, 8), Neon.dp(this, 6))
        list.addView(t, lp)
        typingView = t
        scrollBottom()
    }

    private fun hideTyping() {
        typingView?.let { list.removeView(it) }
        typingView = null
    }

    private fun scrollBottom() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun loadHistory() {
        try {
            val arr = JSONArray(Prefs.chatJson)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val role = o.optString("role")
                val text = o.optString("text")
                history.add(AiClient.Msg(role, text))
                addBubble(if (role == "user") "user" else "ai", text)
            }
        } catch (_: Exception) { }
    }

    private fun saveHistory() {
        try {
            val arr = JSONArray()
            synchronized(history) {
                for (m in history.takeLast(60)) arr.put(JSONObject().put("role", m.role).put("text", m.text))
            }
            Prefs.chatJson = arr.toString()
        } catch (_: Exception) { }
    }

    override fun onEvent(e: JobBus.Event) {
        runOnUiThread {
            when (e.type) {
                "render_done" -> {
                    JobBus.status = ""
                    updateJobChip("")
                    addResultBubble(e.msg, e.path ?: "")
                }
                "render_error" -> {
                    JobBus.status = ""
                    updateJobChip("")
                    addBubble("ai", e.msg)
                }
                else -> updateJobChip(e.msg + if (e.progress in 0..99) " " + e.progress + "%" else "")
            }
        }
    }

    private fun updateJobChip(s: String) {
        if (s.isEmpty()) { jobChip.visibility = View.GONE } else {
            jobChip.text = s
            jobChip.visibility = View.VISIBLE
        }
    }
}
