package com.gikoko.recap.recap

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.net.RecapPlan
import com.gikoko.recap.net.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RecapOutput(val video: File, val srt: File, val title: String)

object RecapPipeline {

    suspend fun runFromFile(
        context: Context,
        srcFile: File,
        apiKey: String,
        voice: String,
        targetMinutes: Int,
        wantCaptions: Boolean,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit
    ): RecapOutput {
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        val prep = withContext(Dispatchers.IO) {
            onStep("Video ကို Gemini ဆီ တင်နေသည်...")
            val mime = GeminiClient.guessMime(srcFile.name)
            val fileName = GeminiClient.uploadVideo(apiKey, srcFile, mime, srcFile.name)
            onLog("Upload: " + fileName)
            val ready = GeminiClient.waitForFileReady(apiKey, fileName, onLog)
            onStep("AI နဲ့ ခြုံငုံသုံးသပ်နေသည်...")
            val p = GeminiClient.generatePlan(apiKey, ready, targetMinutes, onLog)
            onStep("မြန်မာအသံ ထုတ်နေသည်...")
            val w = genWavs(cache, apiKey, p.segments, voice, onLog)
            Pair(p, w)
        }
        val plan = prep.first
        val wavs = prep.second

        onStep("Recap video ဖွဲ့စည်းနေသည်...")
        val videoItems = ArrayList<EditedMediaItem>()
        for (i in plan.segments.indices) {
            val seg = plan.segments[i]
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(seg.startSec * 1000L)
                .setEndPositionMs(seg.endSec * 1000L)
                .build()
            val mi = MediaItem.Builder()
                .setUri(Uri.fromFile(srcFile))
                .setClippingConfiguration(clipping)
                .build()
            val b = EditedMediaItem.Builder(mi).setRemoveAudio(true)
            if (wantCaptions) {
                val overlay = TextOverlay.createStaticTextOverlay(seg.text)
                b.setEffects(Effects(ImmutableList.of(OverlayEffect(ImmutableList.of(overlay))), ImmutableList.of()))
            }
            videoItems.add(b.build())
        }
        val audioItems = ArrayList<EditedMediaItem>()
        for (w in wavs) {
            val mi = MediaItem.Builder().setUri(Uri.fromFile(w)).build()
            audioItems.add(EditedMediaItem.Builder(mi).build())
        }
        val out = File(context.cacheDir, "recap_out.mp4")
        if (out.exists()) out.delete()
        withContext(Dispatchers.Main) {
            compose(context, videoItems, audioItems, out)
        }
        val srt = File(cache, "recap.srt")
        withContext(Dispatchers.IO) {
            srt.writeText(buildSrt(plan.segments, wavs))
        }
        return RecapOutput(out, srt, plan.title)
    }

    suspend fun runFromYouTube(
        context: Context,
        url: String,
        apiKey: String,
        voice: String,
        targetMinutes: Int,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit
    ): RecapOutput {
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        val prep = withContext(Dispatchers.IO) {
            onStep("Gemini နဲ့ Video ခြုံငုံနေသည်...")
            val p = GeminiClient.generatePlan(apiKey, url, targetMinutes, onLog)
            onStep("မြန်မာအသံ ထုတ်နေသည်...")
            val w = genWavs(cache, apiKey, p.segments, voice, onLog)
            Pair(p, w)
        }
        val plan = prep.first
        val wavs = prep.second

        onStep("ပုံဖရင်များ ဆွဲနေသည်...")
        withContext(Dispatchers.IO) {
            for (i in plan.segments.indices) {
                val f = File(cache, "frame_" + i + ".jpg")
                FrameRenderer.render(plan.title, plan.segments[i].text, i, f)
            }
        }
        val videoItems = ArrayList<EditedMediaItem>()
        for (i in plan.segments.indices) {
            val durMs = WavUtil.wavDurationMs(wavs[i].readBytes())
            val mi = MediaItem.Builder()
                .setUri(Uri.fromFile(File(cache, "frame_" + i + ".jpg")))
                .setMimeType(MimeTypes.IMAGE_JPEG)
                .build()
            videoItems.add(EditedMediaItem.Builder(mi).setDurationUs(durMs * 1000L).build())
        }
        val audioItems = ArrayList<EditedMediaItem>()
        for (w in wavs) {
            val mi = MediaItem.Builder().setUri(Uri.fromFile(w)).build()
            audioItems.add(EditedMediaItem.Builder(mi).build())
        }
        val out = File(context.cacheDir, "recap_out.mp4")
        if (out.exists()) out.delete()
        withContext(Dispatchers.Main) {
            compose(context, videoItems, audioItems, out)
        }
        val srt = File(cache, "recap.srt")
        withContext(Dispatchers.IO) {
            srt.writeText(buildSrt(plan.segments, wavs))
        }
        return RecapOutput(out, srt, plan.title)
    }

    private suspend fun genWavs(
        cache: File,
        apiKey: String,
        segs: List<Segment>,
        voice: String,
        onLog: (String) -> Unit
    ): ArrayList<File> = withContext(Dispatchers.IO) {
        val list = ArrayList<File>()
        for (i in segs.indices) {
            onLog("TTS segment " + (i + 1) + "/" + segs.size + " voice=" + voice)
            val (pcm, rate) = GeminiClient.generateTts(apiKey, segs[i].text, voice)
            val wav = WavUtil.pcmToWav(pcm, rate)
            val f = File(cache, "seg_" + i + ".wav")
            f.writeBytes(wav)
            list.add(f)
        }
        list
    }

    private suspend fun compose(
        context: Context,
        videoItems: List<EditedMediaItem>,
        audioItems: List<EditedMediaItem>,
        out: File
    ) {
        val videoSeq = EditedMediaItemSequence.Builder(ImmutableList.copyOf(videoItems)).build()
        val audioSeq = EditedMediaItemSequence.Builder(ImmutableList.copyOf(audioItems)).build()
        val composition = Composition.Builder(ImmutableList.of(videoSeq, audioSeq)).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()
        suspendCancellableCoroutine { cont ->
            transformer.startComposition(composition, out.absolutePath, object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }
        }
    }

    fun buildSrt(segments: List<Segment>, wavs: List<File>): String {
        val sb = StringBuilder()
        var t = 0L
        for (i in segments.indices) {
            val d = WavUtil.wavDurationMs(wavs[i].readBytes())
            val start = t
            val end = t + d
            t = end
            sb.append(i + 1).append('\n')
            sb.append(srtTime(start)).append(" --> ").append(srtTime(end)).append('\n')
            sb.append(segments[i].text).append('\n').append('\n')
        }
        return sb.toString()
    }

    fun srtTime(ms: Long): String {
        val h = ms / 3600000L
        val m = (ms % 3600000L) / 60000L
        val s = (ms % 60000L) / 1000L
        val milli = ms % 1000L
        return String.format("%02d:%02d:%02d,%03d", h, m, s, milli)
    }
}
