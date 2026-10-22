package com.gikoko.recap

import android.app.Application
import android.util.Log
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "last_crash.txt").writeText(
                    "app: GIkoko One Recap v2.1.1\n" + "thread: " + t.name + "\n\n" + Log.getStackTraceString(e)
                )
            } catch (ignore: Exception) {
            }
            def?.uncaughtException(t, e)
        }
    }
}
