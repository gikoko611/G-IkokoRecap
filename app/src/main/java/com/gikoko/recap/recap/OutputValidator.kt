package com.gikoko.recap.recap

import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
import java.io.File
import java.io.IOException

object OutputValidator {
    fun validateVideo(file: File, requireAudio: Boolean = true) {
        if (!file.exists() || !file.isFile || file.length() < 4096L) {
            throw IOException("Output video မရှိပါ သို့မဟုတ် ဖိုင်အရွယ်အစား မမှန်ပါ။")
        }
        val extractor = MediaExtractor()
        var hasVideo = false
        var hasAudio = false
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) hasVideo = true
                if (mime.startsWith("audio/")) hasAudio = true
            }
        } catch (e: Exception) {
            throw IOException("Output MP4 ကို ဖွင့်စစ်ဆေးမရပါ: " + (e.message ?: "Media format error"), e)
        } finally {
            extractor.release()
        }
        if (!hasVideo) throw IOException("Output MP4 ထဲမှာ Video Track မတွေ့ပါ။")
        if (requireAudio && !hasAudio) throw IOException("Output MP4 ထဲမှာ Audio Track မတွေ့ပါ။")
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (duration <= 0L) throw IOException("Output Video duration မမှန်ပါ။")
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Output Video metadata စစ်ဆေးမရပါ။", e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun validateSrt(file: File, expectedCues: Int) {
        if (!file.exists() || !file.isFile || file.length() < 16L) {
            throw IOException("Subtitle SRT file မရှိပါ သို့မဟုတ် empty ဖြစ်နေပါသည်။")
        }
        val text = file.readText()
        val cues = text.lineSequence().count { it.contains(" --> ") }
        if (cues != expectedCues || cues == 0) {
            throw IOException("Subtitle validation failed: expected " + expectedCues + " cues, found " + cues)
        }
    }

    fun validateAudio(file: File) {
        if (!file.exists() || !file.isFile || file.length() < 1024L) {
            throw IOException("Output audio မရှိပါ သို့မဟုတ် ဖိုင်အရွယ်အစား မမှန်ပါ။")
        }
        val extractor = MediaExtractor()
        var hasAudio = false
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) hasAudio = true
            }
        } catch (e: Exception) {
            throw IOException("Output Audio ကို ဖွင့်စစ်ဆေးမရပါ။", e)
        } finally {
            extractor.release()
        }
        if (!hasAudio) throw IOException("Output file ထဲမှာ Audio Track မတွေ့ပါ။")
    }
}
