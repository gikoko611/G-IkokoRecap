package com.gikoko.recap

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    fun log(ctx: Context, event: String) {
        runCatching {
            val f = File(ctx.filesDir, "breadcrumbs.txt")
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val line = time + " " + event
            val old = if (f.exists()) f.readText() else ""
            val lines = (old + "\n" + line).split("\n").filter { it.isNotBlank() }
            f.writeText(lines.takeLast(150).joinToString("\n"))
        }
    }

    fun read(ctx: Context): String {
        val sb = StringBuilder()
        runCatching {
            val cr = File(ctx.filesDir, "last_crash.txt")
            if (cr.exists() && cr.length() > 0) {
                sb.append("=== LAST CRASH ===\n").append(cr.readText().take(2500)).append("\n\n")
            }
            val bc = File(ctx.filesDir, "breadcrumbs.txt")
            if (bc.exists()) sb.append("=== RECENT ACTIONS ===\n").append(bc.readText())
            if (sb.isEmpty()) sb.append("log မရှိပါ - crash မဖြစ်ရသေးပါ")
        }
        return sb.toString()
    }

    fun clear(ctx: Context) {
        runCatching {
            File(ctx.filesDir, "breadcrumbs.txt").delete()
            File(ctx.filesDir, "last_crash.txt").delete()
        }
    }
}
