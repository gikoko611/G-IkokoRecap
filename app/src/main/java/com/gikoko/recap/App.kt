package com.gikoko.recap

import android.app.Application
import android.util.Log
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLog.log(this, "app start")
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "last_crash.txt").writeText(
                    "app: GIKOKO RECAP v2.0\n" + "thread: " + t.name + "\n\n" + Log.getStackTraceString(e)
                )
            } catch (ignore: Exception) {
            }
            def?.uncaughtException(t, e)
        }
    }
}
