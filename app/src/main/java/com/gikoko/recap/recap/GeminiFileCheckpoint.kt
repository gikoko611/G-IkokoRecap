package com.gikoko.recap.recap

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object GeminiFileCheckpoint {
    private val maxAgeMs = TimeUnit.HOURS.toMillis(47)

    private fun stateFile(context: Context, source: File): File {
        val identity = source.absolutePath + "|" + source.length() + "|" + source.lastModified()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val dir = File(context.filesDir, "gemini_file_refs").apply { mkdirs() }
        return File(dir, hash + ".json")
    }

    fun load(context: Context, source: File): String? {
        val file = stateFile(context, source)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
            file.delete()
            return null
        }
        return runCatching { JSONObject(file.readText()).optString("fileName").takeIf { it.isNotBlank() } }.getOrNull()
    }

    fun save(context: Context, source: File, fileName: String) {
        runCatching {
            val target = stateFile(context, source)
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(
                JSONObject()
                    .put("fileName", fileName)
                    .put("fileLength", source.length())
                    .put("lastModified", source.lastModified())
                    .put("savedAt", System.currentTimeMillis())
                    .toString()
            )
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }

    fun clear(context: Context, source: File) {
        runCatching { stateFile(context, source).delete() }
    }
}
