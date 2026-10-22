package com.gikoko.recap.recap

import android.content.Context
import android.net.Uri
import android.text.SpannableString
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
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
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RecapConfig(
    val apiKey: String,
    val provider: String,
    val baseUrl: String,
    val customKey: String,
    val customModel: String,
    val model: String,
    val voice: String,
    val targetMinutes: Int,
    val wantCaptions: Boolean,
    val lang: String,
    val fullSegments: Boolean,
    val watermark: Boolean,
    val introOutro: Boolean,
    val quality: Int,
    val style: String,
    val speed: Double,
    val customStyle: String,
    val vertical: Boolean
)

data class RecapOutput(val video: File, val srt: File, val title: String, val summary: String)

object RecapPipeline {

    suspend fun runFromFile(
        context: Context,
        srcFile: File,
        cfg: RecapConfig,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): RecapOutput {
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        onLog("Recap style: " + cfg.style + "  speed=" + cfg.speed + "  vertical=" + cfg.vertical)
        val prep = withContext(Dispatchers.IO) {
            val plan: RecapPlan
            if (cfg.provider == "custom") {
                onStep("Custom provider နဲ့ ခြုံငုံနေသည်...")
                onProgress(3)
                val mb = srcFile.length() / (1024.0 * 1024.0)
                if (mb > 24) throw IOException("Custom provider နဲ့ Video File mode မှာ 24MB အောက်ပဲရတယ်။ YouTube mode ကို သုံးပါ။")
                onLog("Encoding file " + String.format("%.1f", mb) + " MB")
                onProgress(18)
                val b64 = android.util.Base64.encodeToString(srcFile.readBytes(), android.util.Base64.NO_WRAP)
                plan = GeminiClient.generatePlanCustom(cfg.baseUrl, cfg.customKey, cfg.customModel, "data:video/mp4;base64," + b64, true, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, cfg.customStyle, onLog)
                onProgress(45)
            } else {
                onStep("Video ကို Gemini ဆီ တင်နေသည်...")
                onProgress(3)
                val mime = GeminiClient.guessMime(srcFile.name)
                val fileName = GeminiClient.uploadVideo(cfg.apiKey, srcFile, mime, srcFile.name)
                onLog("Upload: " + fileName)
                onProgress(8)
                val ready = GeminiClient.waitForFileReady(cfg.apiKey, fileName, onLog)
                onProgress(12)
                onStep("AI နဲ့ ခြုံငုံသုံးသပ်နေသည်...")
                onProgress(18)
                plan = GeminiClient.generatePlan(cfg.apiKey, cfg.model, ready, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, cfg.customStyle, onLog)
                onProgress(45)
            }
            onStep("အသံထုတ်နေသည်...")
            val w = genWavs(cache, cfg, plan.segments, onLog, onProgress, 45, 20)
            Pair(plan, w)
        }
        val plan = prep.first
        val wavs = prep.second

        onStep("Recap video ဖွဲ့စည်းနေသည်...")
        onProgress(68)
        var cropL = 0f
        var cropR = 0f
        var cropT = 0f
        var cropB = 0f
        if (cfg.vertical) {
            runCatching {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(srcFile.absolutePath)
                val vw = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                val vh = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                mmr.release()
                val target = 9.0 / 16.0
                if (vw.toDouble() / vh.toDouble() > target) {
                    val c = ((1.0 - (vh * target) / vw) / 2.0).toFloat()
                    cropL = c
                    cropR = c
                } else {
                    val c = ((1.0 - (vw / target) / vh) / 2.0).toFloat()
                    cropT = c
                    cropB = c
                }
            }
        }
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
            val videoEffects = ArrayList<Effect>()
            if (cfg.wantCaptions) {
                val overlay = TextOverlay.createStaticTextOverlay(SpannableString(seg.text))
                val texList: ImmutableList<TextureOverlay> = ImmutableList.of(overlay)
                videoEffects.add(OverlayEffect(texList))
            }
            if (cfg.vertical) {
                videoEffects.add(androidx.media3.effect.Crop(cropL, cropR, cropT, cropB))
                videoEffects.add(Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT))
            } else {
                videoEffects.add(Presentation.createForHeight(if (cfg.quality == 0) 720 else 1080))
            }
            val effectList: List<Effect> = ImmutableList.copyOf(videoEffects)
            b.setEffects(Effects(ImmutableList.of(), effectList))
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
        onProgress(96)
        val srt = File(cache, "recap.srt")
        withContext(Dispatchers.IO) {
            srt.writeText(buildSrt(plan.segments, wavs, 0))
        }
        var summary = plan.segments.joinToString(" ") { s -> s.text }
        if (plan.chapters.isNotEmpty()) summary += "\n\nCHAPTERS\n" + plan.chapters.joinToString("\n")
        if (plan.quiz.isNotEmpty()) summary += "\n\nQUIZ\n" + plan.quiz.joinToString("\n")
        return RecapOutput(out, srt, plan.title, summary)
    }

    suspend fun runFromYouTube(
        context: Context,
        url: String,
        cfg: RecapConfig,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): RecapOutput {
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        onLog("Recap style: " + cfg.style + "  speed=" + cfg.speed + "  vertical=" + cfg.vertical)
        val prep = withContext(Dispatchers.IO) {
            onStep("Gemini နဲ့ Video ခြုံငုံနေသည်...")
            onProgress(18)
            val plan: RecapPlan
            if (cfg.provider == "custom") {
                plan = GeminiClient.generatePlanCustom(cfg.baseUrl, cfg.customKey, cfg.customModel, url, false, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, cfg.customStyle, onLog)
            } else {
                plan = GeminiClient.generatePlan(cfg.apiKey, cfg.model, url, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, cfg.customStyle, onLog)
            }
            onProgress(45)
            onStep("အသံထုတ်နေသည်...")
            val w = genWavs(cache, cfg, plan.segments, onLog, onProgress, 45, 20)
            Pair(plan, w)
        }
        val plan = prep.first
        val wavs = prep.second

        onStep("ပုံဖရင်များ ဆွဲနေသည်...")
        val nSeg = plan.segments.size
        withContext(Dispatchers.IO) {
            for (i in plan.segments.indices) {
                val f = File(cache, "frame_" + i + ".jpg")
                FrameRenderer.render(plan.title, plan.segments[i].text, i, f, cfg.watermark, cfg.vertical)
                onProgress(66 + 6 * (i + 1) / nSeg)
            }
            if (cfg.introOutro) {
                FrameRenderer.renderBranded(File(cache, "intro.jpg"), true, cfg.watermark, cfg.vertical)
                FrameRenderer.renderBranded(File(cache, "outro.jpg"), false, cfg.watermark, cfg.vertical)
            }
        }
        onStep("Recap video ဖွဲ့စည်းနေသည်...")
        onProgress(74)
        val videoItems = ArrayList<EditedMediaItem>()
        if (cfg.introOutro) {
            videoItems.add(imageItem(File(cache, "intro.jpg"), 2500000L))
        }
        for (i in plan.segments.indices) {
            val durMs = WavUtil.wavDurationMs(wavs[i].readBytes())
            videoItems.add(imageItem(File(cache, "frame_" + i + ".jpg"), durMs * 1000L))
        }
        if (cfg.introOutro) {
            videoItems.add(imageItem(File(cache, "outro.jpg"), 2500000L))
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
        onProgress(96)
        val srt = File(cache, "recap.srt")
        withContext(Dispatchers.IO) {
            srt.writeText(buildSrt(plan.segments, wavs, if (cfg.introOutro) 2500 else 0))
        }
        var summary = plan.segments.joinToString(" ") { s -> s.text }
        if (plan.chapters.isNotEmpty()) summary += "\n\nCHAPTERS\n" + plan.chapters.joinToString("\n")
        if (plan.quiz.isNotEmpty()) summary += "\n\nQUIZ\n" + plan.quiz.joinToString("\n")
        return RecapOutput(out, srt, plan.title, summary)
    }

    private fun imageItem(f: File, durUs: Long): EditedMediaItem {
        val mi = MediaItem.Builder()
            .setUri(Uri.fromFile(f))
            .setMimeType(MimeTypes.IMAGE_JPEG)
            .build()
        return EditedMediaItem.Builder(mi).setDurationUs(durUs).build()
    }

    private suspend fun genWavs(
        cache: File,
        cfg: RecapConfig,
        segs: List<Segment>,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit,
        base: Int,
        span: Int
    ): ArrayList<File> = withContext(Dispatchers.IO) {
        val list = ArrayList<File>()
        for (i in segs.indices) {
            onLog("TTS segment " + (i + 1) + "/" + segs.size + " voice=" + cfg.voice)
            val (pcm, rate) = GeminiClient.generateTts(cfg.apiKey, segs[i].text, cfg.voice, cfg.speed)
            val wav = WavUtil.pcmToWav(pcm, rate)
            val f = File(cache, "seg_" + i + ".wav")
            f.writeBytes(wav)
            list.add(f)
            onProgress(base + span * (i + 1) / segs.size)
        }
        list
    }

    private suspend fun compose(
        context: Context,
        videoItems: List<EditedMediaItem>,
        audioItems: List<EditedMediaItem>,
        out: File
    ) {
        val videoSeq = EditedMediaItemSequence(ImmutableList.copyOf(videoItems))
        val audioSeq = EditedMediaItemSequence(ImmutableList.copyOf(audioItems))
        val composition = Composition.Builder(videoSeq, audioSeq).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    composeDone?.invoke(null)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    composeDone?.invoke(exportException)
                }
            })
            .build()
        suspendCancellableCoroutine { cont ->
            composeDone = { err: ExportException? ->
                composeDone = null
                if (err == null) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    if (cont.isActive) cont.resumeWithException(err)
                }
            }
            transformer.start(composition, out.absolutePath)
            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }
        }
    }

    private var composeDone: ((ExportException?) -> Unit)? = null

    fun buildSrt(segments: List<Segment>, wavs: List<File>, offsetMs: Long): String {
        val sb = StringBuilder()
        var t = offsetMs
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
