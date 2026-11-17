package com.gikoko.giai

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TermuxClient {
    const val SHARED_DIR = "/sdcard/Android/data/com.gikoko.giai/files"

    private fun candidates(): List<String> =
        listOf("com.termux", "com.termux.monet", "com.termux.x11", "com.termux.styling")

    /** Find any installed Termux that exposes the RUN_COMMAND service (works for forks too). */
    fun servicePackage(ctx: Context): String? {
        val pm = ctx.packageManager
        for (pkg in candidates()) {
            try {
                pm.getServiceInfo(ComponentName(pkg, "com.termux.app.RunCommandService"), 0)
                return pkg
            } catch (_: Exception) { }
        }
        return try {
            val i = Intent("com.termux.RUN_COMMAND_ACTION")
            val svcs = pm.queryIntentServices(i, 0)
            if (svcs.isNotEmpty()) svcs[0].serviceInfo.packageName else null
        } catch (_: Exception) { null }
    }

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (_: Exception) { servicePackage(ctx) != null }

    fun apiInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux.api", 0)
        true
    } catch (_: Exception) { false }

    /**
     * Run a shell script inside Termux via RUN_COMMAND. Needs allow-external-apps=true in Termux.
     * Returns null on success, or a Burmese reason string on failure.
     */
    fun run(ctx: Context, script: String): String? {
        val pkg = servicePackage(ctx)
            ?: return "❌ Termux app မတွေ့ပါ (Termux ကို F-Droid သို့မဟုတ် GitHub release ကနေ install လုပ်ပါ)"
        val i = Intent("com.termux.RUN_COMMAND_ACTION")
        i.component = ComponentName(pkg, "com.termux.app.RunCommandService")
        i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/" + pkg + "/files/usr/bin/bash")
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/" + pkg + "/files/home")
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            null
        } catch (e: Exception) {
            // fallback: action-only intent
            try {
                val i2 = Intent("com.termux.RUN_COMMAND_ACTION")
                i2.setPackage(pkg)
                i2.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/" + pkg + "/files/usr/bin/bash")
                i2.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
                i2.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/" + pkg + "/files/home")
                i2.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i2) else ctx.startService(i2)
                null
            } catch (e2: Exception) {
                "⚠ Termux တွေ့ပြီး command မပို့နိုင်ပါ: " + (e2.message ?: "unknown") +
                    "\nTermux ဗားရှင်းအသစ် (GitHub release 0.95+) install လုပ်ပါ"
            }
        }
    }

    fun writeShared(ctx: Context, name: String, content: String): java.io.File {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = java.io.File(dir, name)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
    }
}
