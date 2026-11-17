package com.gikoko.giai

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DownloadsUtil {
    fun saveFrom(ctx: Context, src: File, displayName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(displayName))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GIAI")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { os -> src.inputStream().use { it.copyTo(os) } }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GIAI")
                dir.mkdirs()
                src.copyTo(File(dir, displayName), overwrite = true)
                Uri.fromFile(File(dir, displayName))
            }
        } catch (_: Exception) { null }
    }

    fun saveBytes(ctx: Context, bytes: ByteArray, displayName: String): Uri? {
        val tmp = File(ctx.cacheDir, displayName)
        tmp.writeBytes(bytes)
        val u = saveFrom(ctx, tmp, displayName)
        tmp.delete()
        return u
    }

    fun zipDir(dir: File, out: File) {
        ZipOutputStream(FileOutputStream(out)).use { z ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(dir).path))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    fun mimeOf(name: String): String = when {
        name.endsWith(".mp4") -> "video/mp4"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        name.endsWith(".zip") -> "application/zip"
        name.endsWith(".html") -> "text/html"
        name.endsWith(".js") -> "text/javascript"
        name.endsWith(".json") -> "application/json"
        name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".py") || name.endsWith(".sh") -> "text/plain"
        else -> "application/octet-stream"
    }
}
