package com.gikoko.giai

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TermuxClient {
    const val SHARED_DIR = "/sdcard/Android/data/com.gikoko.giai/files"

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (_: Exception) { false }

    fun apiInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux.api", 0)
        true
    } catch (_: Exception) { false }

    /** Run a shell script inside Termux via RUN_COMMAND. Needs allow-external-apps=true in Termux. */
    fun run(ctx: Context, script: String): Boolean {
        if (!installed(ctx)) return false
        val i = Intent("com.termux.RUN_COMMAND_ACTION")
        i.component = ComponentName("com.termux", "com.termux.app.RunCommandService")
        i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            true
        } catch (_: Exception) { false }
    }

    fun writeShared(ctx: Context, name: String, content: String): java.io.File {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = java.io.File(dir, name)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
    }
}
