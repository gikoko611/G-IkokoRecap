package com.gikoko.recap.recap

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

object CacheMaintenance {
    private val maxAgeMs = TimeUnit.DAYS.toMillis(7)
    private const val MAX_TTS_CACHE_BYTES = 250L * 1024L * 1024L
    private const val MAX_FRAME_CACHE_BYTES = 250L * 1024L * 1024L

    fun prune(context: Context, onLog: (String) -> Unit) {
        val now = System.currentTimeMillis()
        val tts = File(context.cacheDir, "tts_cache")
        val frames = File(context.cacheDir, "recap_work")
        var deleted = 0
        if (tts.exists()) {
            tts.listFiles()?.filter { it.isFile && now - it.lastModified() > maxAgeMs }?.forEach {
                if (it.delete()) deleted++
            }
            pruneFilesBySize(tts, MAX_TTS_CACHE_BYTES)
        }
        if (frames.exists()) {
            frames.listFiles()?.filter { it.isDirectory && it.name.startsWith("frames_") && now - it.lastModified() > maxAgeMs }?.forEach {
                if (it.deleteRecursively()) deleted++
            }
            pruneDirectoriesBySize(frames, MAX_FRAME_CACHE_BYTES)
        }
        if (deleted > 0) onLog("CACHE CLEANUP: removed " + deleted + " expired cache item(s)")
    }

    private fun pruneFilesBySize(dir: File, maxBytes: Long) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun pruneDirectoriesBySize(parent: File, maxBytes: Long) {
        val dirs = parent.listFiles()?.filter { it.isDirectory && it.name.startsWith("frames_") }?.sortedBy { it.lastModified() } ?: return
        var total = dirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        for (dir in dirs) {
            if (total <= maxBytes) break
            val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (dir.deleteRecursively()) total -= size
        }
    }
}
