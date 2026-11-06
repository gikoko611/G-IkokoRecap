package com.gikoko.recap.recap

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.io.File
import java.io.IOException

object DeviceGuard {
    private const val MIN_FREE_BYTES = 120L * 1024L * 1024L
    private const val WARN_RAM_BYTES = 160L * 1024L * 1024L

    fun checkInput(context: Context, file: File, onLog: (String) -> Unit) {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            throw IOException("Video file မတွေ့ပါ သို့မဟုတ် ဖတ်လို့မရပါ။ File ကို ပြန်ရွေးပါ။")
        }
        if (file.length() < 1024L) {
            throw IOException("Video file အရွယ်အစား မမှန်ပါ သို့မဟုတ် ဖိုင်ပျက်နေပါသည်။")
        }
        checkStorage(context, MIN_FREE_BYTES, onLog)
        onLog("DEVICE CHECK: input OK (" + String.format("%.1f", file.length() / 1048576.0) + " MB)")
    }

    fun checkRender(context: Context, onLog: (String) -> Unit): Boolean {
        checkStorage(context, MIN_FREE_BYTES, onLog)
        var lowResource = false
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (manager != null) {
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            lowResource = info.lowMemory || info.availMem < WARN_RAM_BYTES
            if (lowResource) {
                onLog("DEVICE WARNING: RAM နည်းနေသည်။ Auto Low-Resolution Mode ကို အသုံးပြုမည်။")
            } else {
                onLog("DEVICE CHECK: available RAM about " + String.format("%.0f", info.availMem / 1048576.0) + " MB")
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (power != null && power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                lowResource = true
                onLog("DEVICE WARNING: ဖုန်းအပူချိန်မြင့်နေသည်။ Auto Low-Resolution Mode ကို အသုံးပြုမည်။")
            }
        }
        if (lowResource) onLog("DEVICE GUARD: render resolution automatically reduced for stability")
        return lowResource
    }

    private fun checkStorage(context: Context, required: Long, onLog: (String) -> Unit) {
        val stats = StatFs(context.filesDir.absolutePath)
        val free = stats.availableBytes
        onLog("STORAGE CHECK: " + String.format("%.0f", free / 1048576.0) + " MB free")
        if (free < required) {
            throw IOException("Storage မလုံလောက်ပါ။ အနည်းဆုံး " + (required / 1048576L) + " MB နေရာလွတ်ထားပြီး ပြန်စမ်းပါ။")
        }
    }
}
