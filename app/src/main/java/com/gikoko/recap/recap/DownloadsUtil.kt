package com.gikoko.recap.recap

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object DownloadsUtil {

    fun saveRecap(context: Context, video: File, srt: File?): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val stamp = System.currentTimeMillis()
                val values = ContentValues()
                values.put(MediaStore.Downloads.DISPLAY_NAME, "GIkokoRecap_" + stamp + ".mp4")
                values.put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                values.put(MediaStore.Downloads.IS_PENDING, 1)
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                val out = resolver.openOutputStream(uri) ?: return false
                out.use { o -> video.inputStream().use { it.copyTo(o) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                if (srt != null && srt.exists()) {
                    val v2 = ContentValues()
                    v2.put(MediaStore.Downloads.DISPLAY_NAME, "GIkokoRecap_" + stamp + ".srt")
                    v2.put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    v2.put(MediaStore.Downloads.IS_PENDING, 1)
                    val u2 = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v2)
                    if (u2 != null) {
                        val o2 = resolver.openOutputStream(u2)
                        if (o2 != null) {
                            o2.use { oo -> srt.inputStream().use { it.copyTo(oo) } }
                        }
                        v2.clear()
                        v2.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(u2, v2, null, null)
                    }
                }
                return true
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                video.copyTo(File(dir, video.name), overwrite = true)
                if (srt != null && srt.exists()) srt.copyTo(File(dir, srt.name), overwrite = true)
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }
}
