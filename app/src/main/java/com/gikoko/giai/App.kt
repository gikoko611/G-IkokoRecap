package com.gikoko.giai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("giai_jobs", "G.I AI Jobs", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
