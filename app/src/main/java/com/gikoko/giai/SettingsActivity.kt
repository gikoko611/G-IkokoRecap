package com.gikoko.giai

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var statPerm: TextView
    private lateinit var statTermux: TextView

    private val permReq = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        updatePermStatus()
        Toast.makeText(this, "✅ permission updated", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Neon.BG)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, Neon.dp(this, 24), 0, Neon.dp(this, 32))
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)

        val title = Neon.label(this, "⚙ G.I AI SETTINGS", 20f, Neon.GREEN, true)
        title.setPadding(Neon.dp(this, 16), 0, 0, Neon.dp(this, 8))
        root.addView(title)

        root.addView(Neon.section(this, "🤖 AI PROVIDER (CUSTOM KEY + FREE FILTER)"))
        val inBase = Neon.input(this, "Base URL (OpenAI-compatible)", Prefs.aiBase)
        val inKey = Neon.input(this, "API Key", Prefs.aiKey, true)
        val inModel = Neon.input(this, "Model name", Prefs.aiModel)
        val freeSw = Switch(this)
        freeSw.text = "  FREE MODELS ONLY (filter :free)"
        freeSw.isChecked = Prefs.aiFreeOnly
        freeSw.setTextColor(Neon.TEXT)
        freeSw.setPadding(Neon.dp(this, 16), Neon.dp(this, 8), 0, 0)
        listOf(inBase, inKey, inModel).forEach {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(Neon.dp(this, 16), Neon.dp(this, 6), Neon.dp(this, 16), 0)
            root.addView(it, lp)
        }
        root.addView(freeSw)
        val aiRow = LinearLayout(this)
        aiRow.orientation = LinearLayout.HORIZONTAL
        aiRow.setPadding(Neon.dp(this, 16), Neon.dp(this, 10), Neon.dp(this, 16), 0)
        aiRow.addView(Neon.btn(this, "🔄 FETCH MODELS") {
            lifecycleScope.launch {
                try {
                    val models = withContext(Dispatchers.IO) { AiClient.models(this@SettingsActivity, freeSw.isChecked) }
                    if (models.isEmpty()) { Toast.makeText(this@SettingsActivity, "no models matched", Toast.LENGTH_SHORT).show(); return@launch }
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Models (" + models.size + ")")
                        .setItems(models.take(60).toTypedArray()) { _, w -> inModel.setText(models[w]) }
                        .show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        aiRow.addView(Neon.btn(this, "✅ TEST") {
            saveAi(inBase, inKey, inModel, freeSw)
            lifecycleScope.launch {
                try {
                    val r = withContext(Dispatchers.IO) {
                        AiClient.chat(this@SettingsActivity, listOf(AiClient.Msg("user", "ping - reply OK only")))
                    }
                    Toast.makeText(this@SettingsActivity, "✅ " + r.take(80), Toast.LENGTH_LONG).show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        root.addView(aiRow)

        root.addView(Neon.section(this, "🐙 GITHUB (TOKEN: READ / WRITE / CREATE / DELETE / ACTIONS)"))
        val inToken = Neon.input(this, "GitHub Personal Access Token (repo scope)", Prefs.ghToken, true)
        val inRepo = Neon.input(this, "Default repo (owner/name)", Prefs.ghRepo)
        listOf(inToken, inRepo).forEach {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(Neon.dp(this, 16), Neon.dp(this, 6), Neon.dp(this, 16), 0)
            root.addView(it, lp)
        }
        val ghRow1 = LinearLayout(this)
        ghRow1.orientation = LinearLayout.HORIZONTAL
        ghRow1.setPadding(Neon.dp(this, 16), Neon.dp(this, 10), Neon.dp(this, 16), 0)
        ghRow1.addView(Neon.btn(this, "💾 SAVE") {
            Prefs.ghToken = inToken.text.toString().trim()
            Prefs.ghRepo = inRepo.text.toString().trim()
            Toast.makeText(this, "saved", Toast.LENGTH_SHORT).show()
        })
        ghRow1.addView(Neon.btn(this, "📁 LIST REPOS") {
            lifecycleScope.launch {
                try {
                    val repos = withContext(Dispatchers.IO) { GitHubClient.listRepos() }
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Repos (" + repos.size + ")")
                        .setItems(repos.take(60).toTypedArray()) { _, w -> inRepo.setText(repos[w]) }
                        .show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        root.addView(ghRow1)
        val ghRow2 = LinearLayout(this)
        ghRow2.orientation = LinearLayout.HORIZONTAL
        ghRow2.setPadding(Neon.dp(this, 16), Neon.dp(this, 6), Neon.dp(this, 16), 0)
        ghRow2.addView(Neon.btn(this, "READ") {
            lifecycleScope.launch {
                try {
                    val repo = inRepo.text.toString().trim()
                    val c = withContext(Dispatchers.IO) { GitHubClient.getFile(repo, "README.md") }
                    Toast.makeText(this@SettingsActivity, "✅ README " + c.length + " chars", Toast.LENGTH_LONG).show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        ghRow2.addView(Neon.btn(this, "WRITE") {
            lifecycleScope.launch {
                try {
                    val repo = inRepo.text.toString().trim()
                    withContext(Dispatchers.IO) {
                        GitHubClient.putFile(repo, "giai-test.txt", "G.I AI test " + System.currentTimeMillis(), "G.I AI write test")
                    }
                    Toast.makeText(this@SettingsActivity, "✅ giai-test.txt created/updated", Toast.LENGTH_LONG).show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        ghRow2.addView(Neon.btn(this, "DELETE") {
            lifecycleScope.launch {
                try {
                    val repo = inRepo.text.toString().trim()
                    withContext(Dispatchers.IO) { GitHubClient.deleteFile(repo, "giai-test.txt") }
                    Toast.makeText(this@SettingsActivity, "✅ giai-test.txt deleted", Toast.LENGTH_LONG).show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        root.addView(ghRow2)
        val ghRow3 = LinearLayout(this)
        ghRow3.orientation = LinearLayout.HORIZONTAL
        ghRow3.setPadding(Neon.dp(this, 16), Neon.dp(this, 6), Neon.dp(this, 16), 0)
        ghRow3.addView(Neon.btn(this, "▶ RUN WORKFLOW") {
            val repo = inRepo.text.toString().trim()
            lifecycleScope.launch {
                try {
                    val wfs = withContext(Dispatchers.IO) { GitHubClient.listWorkflows(repo) }
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Run workflow on main")
                        .setItems(wfs.toTypedArray()) { _, w ->
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) { GitHubClient.runWorkflow(repo, wfs[w], "main") }
                                    Toast.makeText(this@SettingsActivity, "✅ dispatched", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
                            }
                        }.show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        ghRow3.addView(Neon.btn(this, "📜 RUNS") {
            lifecycleScope.launch {
                try {
                    val t = withContext(Dispatchers.IO) { GitHubClient.listRuns(inRepo.text.toString().trim()) }
                    val runs = org.json.JSONObject(t).optJSONArray("workflow_runs")
                    val sb = StringBuilder()
                    for (i in 0 until (runs?.length() ?: 0)) {
                        val o = runs!!.optJSONObject(i) ?: continue
                        sb.append(o.optString("name")).append(" #").append(o.optString("run_number"))
                            .append(" = ").append(o.optString("status")).append("/").append(o.optString("conclusion")).append("\n")
                    }
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Recent runs").setMessage(sb.toString().ifEmpty { "none" }).show()
                } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "⚠ " + e.message, Toast.LENGTH_LONG).show() }
            }
        })
        root.addView(ghRow3)

        root.addView(Neon.section(this, "📱 PHONE ACCESS (PERMISSIONS)"))
        statPerm = Neon.note(this, "")
        root.addView(statPerm)
        val permRow = LinearLayout(this)
        permRow.orientation = LinearLayout.HORIZONTAL
        permRow.setPadding(Neon.dp(this, 16), Neon.dp(this, 8), Neon.dp(this, 16), 0)
        permRow.addView(Neon.btn(this, "🔓 GRANT") {
            val ps = ArrayList<String>()
            if (Build.VERSION.SDK_INT >= 33) {
                ps.add(Manifest.permission.READ_MEDIA_IMAGES)
                ps.add(Manifest.permission.READ_MEDIA_VIDEO)
                ps.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ps.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ps.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permReq.launch(ps.toTypedArray())
        })
        root.addView(permRow)

        root.addView(Neon.section(this, "🖥 TERMUX BRIDGE (RUN ALL TERMUX TOOLS)"))
        statTermux = Neon.note(this, "")
        root.addView(statTermux)
        root.addView(Neon.note(this, "Termux ထဲမှာ တစ်ခါတည်း: pkg install termux-api ffmpeg python nodejs\nTermux မှာ allow-external-apps=true ဖွင့်ပါ:\n~/.termux/termux.properties ထဲ allow-external-apps=true ထည့်ပြီး Termux ပိတ်/ဖွင့်ပါ။"))
        val txRow = LinearLayout(this)
        txRow.orientation = LinearLayout.HORIZONTAL
        txRow.setPadding(Neon.dp(this, 16), Neon.dp(this, 8), Neon.dp(this, 16), 0)
        txRow.addView(Neon.btn(this, "▶ TEST COMMAND") {
            val ok = TermuxClient.run(this, "echo G.I AI OK > ~/giai_test.txt && termux-toast 'G.I AI connected' 2>/dev/null; echo done")
            Toast.makeText(this, if (ok) "▶ sent (Termux မျက်နှာပြင်မှာ စစ်ပါ)" else "⚠ Termux မရှိ", Toast.LENGTH_LONG).show()
        })
        txRow.addView(Neon.btn(this, "📋 COPY SHARED PATH") {
            val cb = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("p", TermuxClient.SHARED_DIR))
            Toast.makeText(this, "copied: shared folder path", Toast.LENGTH_SHORT).show()
        })
        root.addView(txRow)

        root.addView(Neon.section(this, "🎬 3D RENDER ENGINE (THREE.JS + H.264)"))
        val inW = Neon.input(this, "Width px", Prefs.renderW.toString())
        val inH = Neon.input(this, "Height px", Prefs.renderH.toString())
        val inFps = Neon.input(this, "FPS", Prefs.renderFps.toString())
        val inFrames = Neon.input(this, "Frames", Prefs.renderFrames.toString())
        listOf(inW, inH, inFps, inFrames).forEach {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(Neon.dp(this, 16), Neon.dp(this, 6), Neon.dp(this, 16), 0)
            root.addView(it, lp)
        }
        val saveBtn = Neon.btn(this, "💾 SAVE 3D SETTINGS") {
            try {
                Prefs.renderW = inW.text.toString().toInt().coerceIn(160, 1080)
                Prefs.renderH = inH.text.toString().toInt().coerceIn(160, 1920)
                Prefs.renderFps = inFps.text.toString().toInt().coerceIn(5, 60)
                Prefs.renderFrames = inFrames.text.toString().toInt().coerceIn(8, 600)
                Toast.makeText(this, "saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "⚠ numbers only", Toast.LENGTH_SHORT).show() }
        }
        val sbLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sbLp.setMargins(Neon.dp(this, 16), Neon.dp(this, 10), 0, 0)
        root.addView(saveBtn, sbLp)

        updatePermStatus()
        updateTermuxStatus()
    }

    private fun saveAi(base: TextView, key: TextView, model: TextView, freeSw: Switch) {
        Prefs.aiBase = base.text.toString().trim().ifEmpty { "https://openrouter.ai/api/v1" }
        Prefs.aiKey = key.text.toString().trim()
        Prefs.aiModel = model.text.toString().trim().ifEmpty { "openrouter/auto" }
        Prefs.aiFreeOnly = freeSw.isChecked
        Toast.makeText(this, "AI provider saved", Toast.LENGTH_SHORT).show()
    }

    private fun updatePermStatus() {
        val sb = StringBuilder("Storage/Media: ")
        val okMedia = if (Build.VERSION.SDK_INT >= 33)
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        else checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        sb.append(if (okMedia) "✅" else "❌")
        if (Build.VERSION.SDK_INT >= 33) {
            sb.append("  Notifications: ")
            sb.append(if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) "✅" else "❌")
        }
        statPerm.text = sb.toString()
    }

    private fun updateTermuxStatus() {
        statTermux.text = "Termux: " + (if (TermuxClient.installed(this)) "✅ installed" else "❌ not installed") +
            "   Termux:API: " + (if (TermuxClient.apiInstalled(this)) "✅" else "❌") +
            "\nShared folder: " + TermuxClient.SHARED_DIR
    }
}
