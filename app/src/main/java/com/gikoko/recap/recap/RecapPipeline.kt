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
import com.gikoko.recap.L
import com.gikoko.recap.Prefs
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.net.RecapPlan
import com.gikoko.recap.net.Segment
import com.gikoko.recap.net.TranscriptClient
import com.gikoko.recap.net.Tts
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
    val vertical: Boolean,
    val extBase: String = "",
    val extKey: String = "",
    val extModel: String = "",
    val transcriptMode: Boolean = false,
    val transcriptFallback: Boolean = true,
    val wmSize: Int = 100,
    val wmPos: String = "tr",
    val wmLogo: String = "",
    val theme: String = "gold",
    val planConfirm: Boolean = false,
    val shorts: Boolean = false,
    val podcast: Boolean = false,
    val dialogue: Boolean = false,
    val voice2: String = "Kore",
    val bgmOn: Boolean = false,
    val bgmPath: String = "",
    val bgmVol: Double = 0.2
)

data class RecapOutput(val video: File, val srt: File, val title: String, val summary: String, val shorts: File? = null, val podcast: File? = null)

object RecapPipeline {

    suspend fun runFromFile(
        context: Context,
        srcFile: File,
        cfg: RecapConfig,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): RecapOutput {
        val (plan, useFrames) = planFromFile(context, srcFile, cfg, onStep, onLog, onProgress)
        return renderFromFile(context, srcFile, plan, cfg, useFrames, onStep, onLog, onProgress)
    }

    suspend fun runFromYouTube(
        context: Context,
        url: String,
        cfg: RecapConfig,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): RecapOutput {
        val plan = planFromYouTube(context, url, cfg, onStep, onLog, onProgress)
        return renderYouTube(context, plan, cfg, onStep, onLog, onProgress)
    }

    suspend fun planFromFile(
        context: Context,
        srcFile: File,
        cfg: RecapConfig,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): Pair<RecapPlan, Boolean> {
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        onLog("Recap style: " + cfg.style + "  speed=" + cfg.speed + "  vertical=" + cfg.vertical)
        if (cfg.transcriptMode && cfg.extKey.isNotEmpty()) {
            onStep(L.s(context, "Audio ထုတ်နေသည်...", "Extracting audio..."))
            onProgress(5)
            val audio = File(cache, "audio_only.mp4")
            withContext(Dispatchers.Main) { extractAudio(context, srcFile, audio) }
            Prefs.bumpUsage(context, Prefs.KEY_U_ASR, 1)
            onLog("Whisper ASR: " + cfg.extModel)
            val t = withContext(Dispatchers.IO) { GeminiClient.whisperTranscribe(cfg.extBase, cfg.extKey, audio) }
            onLog("Transcript ready: " + t.length + " chars - plan ဆွဲနေသည်")
            val plan = withContext(Dispatchers.IO) { GeminiClient.generatePlanTranscript(cfg.extBase, cfg.extKey, cfg.extModel, t, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, styleIn(cfg), onLog) }
            Prefs.bumpUsage(context, Prefs.KEY_U_EXT, 1)
            onProgress(45)
            return Pair(plan, true)
        }
        return withContext(Dispatchers.IO) {
            val plan: RecapPlan
            if (cfg.provider == "custom") {
                onStep(L.s(context, "Custom provider နဲ့ ခြုံငုံနေသည်...", "Analyzing with custom provider..."))
                onProgress(3)
                val mb = srcFile.length() / (1024.0 * 1024.0)
                if (mb > 24) throw IOException("Custom provider နဲ့ Video File mode မှာ 24MB အောက်ပဲရတယ်။ YouTube mode ကို သုံးပါ။")
                onLog("Encoding file " + String.format("%.1f", mb) + " MB")
                onProgress(18)
                val b64 = android.util.Base64.encodeToString(srcFile.readBytes(), android.util.Base64.NO_WRAP)
                plan = GeminiClient.generatePlanCustom(cfg.baseUrl, cfg.customKey, cfg.customModel, "data:video/mp4;base64," + b64, true, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, styleIn(cfg), onLog)
                Prefs.bumpUsage(context, Prefs.KEY_U_EXT, 1)
                onProgress(45)
            } else {
                onStep(L.s(context, "Video ကို Gemini ဆီ တင်နေသည်...", "Uploading video to Gemini..."))
                onProgress(3)
                val mime = GeminiClient.guessMime(srcFile.name)
                val fileName = GeminiClient.uploadVideo(cfg.apiKey, srcFile, mime, srcFile.name)
                onLog("Upload: " + fileName)
                onProgress(8)
                val ready = GeminiClient.waitForFileReady(cfg.apiKey, fileName, onLog)
                onProgress(12)
                onStep(L.s(context, "AI နဲ့ ခြုံငုံသုံးသပ်နေသည်...", "Analyzing with AI..."))
                onProgress(18)
                plan = GeminiClient.generatePlan(cfg.apiKey, cfg.model, ready, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, styleIn(cfg), onLog)
                Prefs.bumpUsage(context, Prefs.KEY_U_PLAN, 1)
                onProgress(45)
            }
            Pair(plan, false)
        }
    }

    suspend fun planFromYouTube(
        context: Context,
        url: String,
        cfg: RecapConfig,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): RecapPlan {
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        onLog("Recap style: " + cfg.style + "  speed=" + cfg.speed + "  vertical=" + cfg.vertical)
        return withContext(Dispatchers.IO) {
            onStep(L.s(context, "Gemini နဲ့ Video ခြုံငုံနေသည်...", "Analyzing video with Gemini..."))
            onProgress(18)
            val plan: RecapPlan
            if (cfg.transcriptMode && cfg.extKey.isNotEmpty()) {
                plan = transcriptPlan(context, cfg, url, onLog)
            } else if (cfg.provider == "custom") {
                plan = try {
                    val p = GeminiClient.generatePlanCustom(cfg.baseUrl, cfg.customKey, cfg.customModel, url, false, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, styleIn(cfg), onLog)
                    Prefs.bumpUsage(context, Prefs.KEY_U_EXT, 1)
                    p
                } catch (e: Exception) {
                    tryTranscriptFallback(context, e, cfg, url, onLog)
                }
            } else {
                plan = try {
                    val p = GeminiClient.generatePlan(cfg.apiKey, cfg.model, url, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, styleIn(cfg), onLog)
                    Prefs.bumpUsage(context, Prefs.KEY_U_PLAN, 1)
                    p
                } catch (e: Exception) {
                    tryTranscriptFallback(context, e, cfg, url, onLog)
                }
            }
            onProgress(45)
            plan
        }
    }

    suspend fun renderFromFile(
        context: Context,
        srcFile: File,
        plan: RecapPlan,
        cfg: RecapConfig,
        useFrames: Boolean,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): RecapOutput {
        if (useFrames) return renderYouTube(context, plan, cfg, onStep, onLog, onProgress)
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        onStep(L.s(context, "အသံထုတ်နေသည်...", "Generating voice..."))
        val wavs = withContext(Dispatchers.IO) { genWavs(context, cache, cfg, plan.segments, onLog, onProgress, 45, 20) }

        onStep(L.s(context, "Recap video ဖွဲ့စည်းနေသည်...", "Composing recap video..."))
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
        var bgmList: List<EditedMediaItem>? = null
        if (cfg.bgmOn && cfg.bgmPath.isNotEmpty()) {
            var totalMs = 0L
            for (w in wavs) totalMs += WavUtil.wavDurationMs(w.readBytes())
            if (cfg.introOutro) totalMs += 5000L
            bgmList = buildBgmItems(cfg, totalMs)
            if (bgmList.isNotEmpty()) onLog("BGM mix: " + File(cfg.bgmPath).name + " vol=" + cfg.bgmVol)
        }
        withContext(Dispatchers.Main) {
            compose(context, videoItems, audioItems, out, bgmList)
        }
        onProgress(96)
        val srt = File(cache, "recap.srt")
        withContext(Dispatchers.IO) {
            srt.writeText(buildSrt(plan.segments, wavs, 0))
        }
        var summary = plan.segments.joinToString(" ") { s -> s.text }
        if (plan.chapters.isNotEmpty()) summary += "\n\nCHAPTERS\n" + plan.chapters.joinToString("\n")
        if (plan.quiz.isNotEmpty()) summary += "\n\nQUIZ\n" + plan.quiz.joinToString("\n")
        var shortFile: File? = null
        if (cfg.shorts) {
            onStep(L.s(context, "Shorts cut ဆွဲနေသည်...", "Cutting shorts..."))
            shortFile = renderShorts(context, plan, cfg, wavs, false, srcFile, onLog, onProgress)
            if (shortFile != null) onLog("Shorts cut ready (vertical 1080x1920) ✓")
        }
        var podFile: File? = null
        if (cfg.podcast) {
            onStep(L.s(context, "Podcast audio ထုတ်နေသည်...", "Exporting podcast audio..."))
            podFile = exportPodcast(context, wavs, onLog)
            if (podFile != null) onLog("Podcast audio ready ✓")
        }
        return RecapOutput(out, srt, plan.title, summary, shortFile, podFile)
    }

    suspend fun renderYouTube(
        context: Context,
        plan: RecapPlan,
        cfg: RecapConfig,
        onStep: (String) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): RecapOutput {
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        onStep(L.s(context, "အသံထုတ်နေသည်...", "Generating voice..."))
        val wavs = withContext(Dispatchers.IO) { genWavs(context, cache, cfg, plan.segments, onLog, onProgress, 45, 20) }

        onStep(L.s(context, "ပုံဖရင်များ ဆွဲနေသည်...", "Rendering frames..."))
        val nSeg = plan.segments.size
        withContext(Dispatchers.IO) {
            for (i in plan.segments.indices) {
                val f = File(cache, "frame_" + i + ".jpg")
                FrameRenderer.render(plan.title, plan.segments[i].text, i, f, cfg.watermark, cfg.vertical, cfg.wmSize, cfg.wmPos, cfg.wmLogo, cfg.theme)
                onProgress(66 + 6 * (i + 1) / nSeg)
            }
            if (cfg.introOutro) {
                FrameRenderer.renderBranded(File(cache, "intro.jpg"), true, cfg.watermark, cfg.vertical, cfg.wmSize, cfg.wmPos, cfg.wmLogo, cfg.theme)
                FrameRenderer.renderBranded(File(cache, "outro.jpg"), false, cfg.watermark, cfg.vertical, cfg.wmSize, cfg.wmPos, cfg.wmLogo, cfg.theme)
            }
        }
        onStep(L.s(context, "Recap video ဖွဲ့စည်းနေသည်...", "Composing recap video..."))
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
        var bgmList: List<EditedMediaItem>? = null
        if (cfg.bgmOn && cfg.bgmPath.isNotEmpty()) {
            var totalMs = 0L
            for (w in wavs) totalMs += WavUtil.wavDurationMs(w.readBytes())
            if (cfg.introOutro) totalMs += 5000L
            bgmList = buildBgmItems(cfg, totalMs)
            if (bgmList.isNotEmpty()) onLog("BGM mix: " + File(cfg.bgmPath).name + " vol=" + cfg.bgmVol)
        }
        withContext(Dispatchers.Main) {
            compose(context, videoItems, audioItems, out, bgmList)
        }
        onProgress(96)
        val srt = File(cache, "recap.srt")
        withContext(Dispatchers.IO) {
            srt.writeText(buildSrt(plan.segments, wavs, if (cfg.introOutro) 2500 else 0))
        }
        var summary = plan.segments.joinToString(" ") { s -> s.text }
        if (plan.chapters.isNotEmpty()) summary += "\n\nCHAPTERS\n" + plan.chapters.joinToString("\n")
        if (plan.quiz.isNotEmpty()) summary += "\n\nQUIZ\n" + plan.quiz.joinToString("\n")
        var shortFile: File? = null
        if (cfg.shorts) {
            onStep(L.s(context, "Shorts cut ဆွဲနေသည်...", "Cutting shorts..."))
            shortFile = renderShorts(context, plan, cfg, wavs, true, null, onLog, onProgress)
            if (shortFile != null) onLog("Shorts cut ready (vertical 1080x1920) ✓")
        }
        var podFile: File? = null
        if (cfg.podcast) {
            onStep(L.s(context, "Podcast audio ထုတ်နေသည်...", "Exporting podcast audio..."))
            podFile = exportPodcast(context, wavs, onLog)
            if (podFile != null) onLog("Podcast audio ready ✓")
        }
        return RecapOutput(out, srt, plan.title, summary, shortFile, podFile)
    }

    private fun styleIn(cfg: RecapConfig): String {
        if (!cfg.dialogue) return cfg.customStyle
        val base = cfg.customStyle.trim()
        val d = "Write the recap as a lively two-person dialogue: HOST and GUEST take turns, one speaker per segment, alternating."
        return if (base.isEmpty()) d else base + " " + d
    }

    private fun tryTranscriptFallback(context: Context, e: Exception, cfg: RecapConfig, url: String, onLog: (String) -> Unit): RecapPlan {
        val m = (e.message ?: "").lowercase()
        val quota = m.contains("429") || m.contains("resource_exhausted") || m.contains("quota") || m.contains("rate limit")
        if (!quota || !cfg.transcriptFallback || cfg.extKey.isEmpty()) throw e
        onLog("Quota hit - transcript fallback (" + cfg.extModel + ")")
        return transcriptPlan(context, cfg, url, onLog)
    }

    private fun transcriptPlan(context: Context, cfg: RecapConfig, url: String, onLog: (String) -> Unit): RecapPlan {
        val vid = TranscriptClient.extractVideoId(url) ?: throw IOException("YouTube link မှ video ID မရနိုင်ပါ")
        onLog("Fetching transcript: " + vid)
        val t = TranscriptClient.fetchTranscript(vid, onLog)
        onLog("Transcript ready: " + t.length + " chars - plan ဆွဲနေသည်")
        val plan = GeminiClient.generatePlanTranscript(cfg.extBase, cfg.extKey, cfg.extModel, t, cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, styleIn(cfg), onLog)
        Prefs.bumpUsage(context, Prefs.KEY_U_EXT, 1)
        return plan
    }

    private fun imageItem(f: File, durUs: Long): EditedMediaItem {
        val mi = MediaItem.Builder()
            .setUri(Uri.fromFile(f))
            .setMimeType(MimeTypes.IMAGE_JPEG)
            .build()
        return EditedMediaItem.Builder(mi).setDurationUs(durUs).build()
    }

    private suspend fun genWavs(
        context: Context,
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
            val voice = if (cfg.dialogue && i % 2 == 1) cfg.voice2 else cfg.voice
            onLog("TTS segment " + (i + 1) + "/" + segs.size + " voice=" + voice)
            val ev = if (cfg.dialogue && i % 2 == 1) Tts.secondEdgeVoice(context) else Tts.defaultEdgeVoice(context)
            val (pcm, rate) = Tts.generate(context, cfg.apiKey, segs[i].text, voice, cfg.speed, ev)
            val wav = WavUtil.pcmToWav(pcm, rate)
            val f = File(cache, "seg_" + i + ".wav")
            f.writeBytes(wav)
            list.add(f)
            onProgress(base + span * (i + 1) / segs.size)
        }
        Prefs.bumpUsage(context, Prefs.KEY_U_TTS, list.size)
        list
    }

    private suspend fun compose(
        context: Context,
        videoItems: List<EditedMediaItem>,
        audioItems: List<EditedMediaItem>,
        out: File,
        bgmItems: List<EditedMediaItem>? = null
    ) {
        val videoSeq = EditedMediaItemSequence(ImmutableList.copyOf(videoItems))
        val audioSeq = EditedMediaItemSequence(ImmutableList.copyOf(audioItems))
        val seqs = ArrayList<EditedMediaItemSequence>()
        seqs.add(videoSeq)
        seqs.add(audioSeq)
        if (bgmItems != null && bgmItems.isNotEmpty()) {
            seqs.add(EditedMediaItemSequence(ImmutableList.copyOf(bgmItems)))
        }
        val composition = Composition.Builder(seqs).build()
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

    private var extractDone: ((ExportException?) -> Unit)? = null

    private suspend fun extractAudio(context: Context, src: File, out: File) {
        if (out.exists()) out.delete()
        val transformer = Transformer.Builder(context).build()
        suspendCancellableCoroutine { cont ->
            extractDone = { err: ExportException? ->
                extractDone = null
                if (err == null) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    if (cont.isActive) cont.resumeWithException(err)
                }
            }
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    extractDone?.invoke(null)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    extractDone?.invoke(exportException)
                }
            })
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(src))).setRemoveVideo(true).build()
            val seq = EditedMediaItemSequence(ImmutableList.of(item))
            val composition = Composition.Builder(seq).build()
            transformer.start(composition, out.absolutePath)
            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }
        }
    }

    private fun buildBgmItems(cfg: RecapConfig, totalMs: Long): ArrayList<EditedMediaItem> {
        val list = ArrayList<EditedMediaItem>()
        val f = File(cfg.bgmPath)
        if (!f.exists()) return list
        var bgmMs = 0L
        runCatching {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(f.absolutePath)
            val d = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
            bgmMs = d
        }
        if (bgmMs <= 0L) bgmMs = 60000L
        val vol = cfg.bgmVol.toFloat()
        var covered = 0L
        while (covered < totalMs && list.size < 40) {
            val mix = androidx.media3.common.audio.ChannelMixingAudioProcessor()
            mix.putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(1, 1, floatArrayOf(vol)))
            mix.putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(2, 2, floatArrayOf(vol, 0f, 0f, vol)))
            mix.putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(6, 6, floatArrayOf(
                vol, 0f, 0f, 0f, 0f, 0f,
                0f, vol, 0f, 0f, 0f, 0f,
                0f, 0f, vol, 0f, 0f, 0f,
                0f, 0f, 0f, vol, 0f, 0f,
                0f, 0f, 0f, 0f, vol, 0f,
                0f, 0f, 0f, 0f, 0f, vol)))
            val mi = MediaItem.Builder().setUri(Uri.fromFile(f)).build()
            val aps: List<androidx.media3.common.audio.AudioProcessor> = ImmutableList.of(mix)
            list.add(EditedMediaItem.Builder(mi).setEffects(Effects(aps, ImmutableList.of())).build())
            covered += bgmMs
        }
        return list
    }

    private suspend fun renderShorts(
        context: Context,
        plan: RecapPlan,
        cfg: RecapConfig,
        wavs: List<File>,
        useFrames: Boolean,
        srcFile: File?,
        onLog: (String) -> Unit,
        onProgress: (Int) -> Unit
    ): File? {
        if (plan.segments.isEmpty() || wavs.isEmpty()) return null
        val cache = File(context.cacheDir, "recap_work")
        cache.mkdirs()
        val idx = plan.segments.indices.sortedByDescending { i -> WavUtil.wavDurationMs(wavs[i].readBytes()) }.take(3).sorted()
        if (idx.isEmpty()) return null
        onLog("Shorts: segments " + idx.joinToString(","))
        val videoItems = ArrayList<EditedMediaItem>()
        if (!useFrames && srcFile != null && srcFile.exists()) {
            var cropL = 0f
            var cropR = 0f
            var cropT = 0f
            var cropB = 0f
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
            for (i in idx) {
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
                val fx = ArrayList<Effect>()
                if (cfg.wantCaptions) {
                    val overlay = TextOverlay.createStaticTextOverlay(SpannableString(seg.text))
                    val texList: ImmutableList<TextureOverlay> = ImmutableList.of(overlay)
                    fx.add(OverlayEffect(texList))
                }
                fx.add(androidx.media3.effect.Crop(cropL, cropR, cropT, cropB))
                fx.add(Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT))
                val effectList: List<Effect> = ImmutableList.copyOf(fx)
                b.setEffects(Effects(ImmutableList.of(), effectList))
                videoItems.add(b.build())
            }
        } else {
            for (k in idx.indices) {
                val i = idx[k]
                val f = File(cache, "short_" + k + ".jpg")
                FrameRenderer.render(plan.title, plan.segments[i].text, k, f, cfg.watermark, true, cfg.wmSize, cfg.wmPos, cfg.wmLogo, cfg.theme)
                val durMs = WavUtil.wavDurationMs(wavs[i].readBytes())
                videoItems.add(imageItem(f, durMs * 1000L))
            }
        }
        val audioItems = ArrayList<EditedMediaItem>()
        for (i in idx) {
            val mi = MediaItem.Builder().setUri(Uri.fromFile(wavs[i])).build()
            audioItems.add(EditedMediaItem.Builder(mi).build())
        }
        val out = File(context.cacheDir, "recap_short.mp4")
        if (out.exists()) out.delete()
        withContext(Dispatchers.Main) {
            compose(context, videoItems, audioItems, out, null)
        }
        onProgress(99)
        return out
    }

    private suspend fun exportPodcast(context: Context, wavs: List<File>, onLog: (String) -> Unit): File? {
        if (wavs.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val pcmParts = ArrayList<ByteArray>()
            var rate = 24000
            for (w in wavs) {
                val b = w.readBytes()
                if (b.size > 44) {
                    rate = WavUtil.readIntLE(b, 24)
                    pcmParts.add(b.copyOfRange(44, b.size))
                }
            }
            if (pcmParts.isEmpty()) return@withContext null
            var total = 0
            for (p in pcmParts) total += p.size
            val pcm = ByteArray(total)
            var off = 0
            for (p in pcmParts) {
                System.arraycopy(p, 0, pcm, off, p.size)
                off += p.size
            }
            val wavFile = File(context.cacheDir, "podcast_tmp.wav")
            wavFile.writeBytes(WavUtil.pcmToWav(pcm, rate))
            val out = File(context.cacheDir, "recap_podcast.m4a")
            if (out.exists()) out.delete()
            withContext(Dispatchers.Main) { extractAudio(context, wavFile, out) }
            wavFile.delete()
            onLog("Podcast audio: " + String.format("%.1f", out.length() / (1024.0 * 1024.0)) + " MB")
            out
        }
    }

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
