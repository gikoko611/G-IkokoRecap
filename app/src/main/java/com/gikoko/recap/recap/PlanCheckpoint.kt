package com.gikoko.recap.recap

import android.content.Context
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.net.RecapPlan
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Persistent AI-plan checkpoint.
 *
 * This lets a retry after a TTS/render failure reuse the completed AI plan
 * instead of paying for/repeating the Gemini analysis. It deliberately does
 * not claim that an in-flight cloud request or Media3 export can be resumed.
 */
object PlanCheckpoint {
    private const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

    fun keyForFile(file: File, cfg: RecapConfig): String {
        val source = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath) +
            "|" + file.length() + "|" + file.lastModified()
        return fingerprint(source, cfg)
    }

    fun keyForYouTube(url: String, cfg: RecapConfig): String =
        fingerprint(url.trim(), cfg)

    private fun fingerprint(source: String, cfg: RecapConfig): String {
        val settings = listOf(
            source,
            cfg.provider,
            cfg.model,
            cfg.customModel,
            cfg.targetMinutes.toString(),
            cfg.lang,
            cfg.fullSegments.toString(),
            cfg.style,
            cfg.customStyle,
            cfg.dialogue.toString(),
            cfg.seriesCtx
        ).joinToString("|")
        val bytes = MessageDigest.getInstance("SHA-256").digest(settings.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun directory(context: Context): File =
        File(context.filesDir, "recap_checkpoints").apply { mkdirs() }

    fun load(context: Context, key: String): Pair<RecapPlan, Boolean>? {
        val file = File(directory(context), "$key.json")
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
            file.delete()
            return null
        }
        return runCatching {
            val root = JSONObject(file.readText())
            val planJson = root.getString("plan")
            GeminiClient.planFromJson(planJson)
        }.getOrNull()
    }

    fun save(context: Context, key: String, plan: RecapPlan, useFrames: Boolean) {
        runCatching {
            val dir = directory(context)
            val target = File(dir, "$key.json")
            val temp = File(dir, "$key.tmp")
            val root = JSONObject()
                .put("version", 1)
                .put("savedAt", System.currentTimeMillis())
                .put("plan", GeminiClient.planToJson(plan, useFrames))
            temp.writeText(root.toString())
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }
}
