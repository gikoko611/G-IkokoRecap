package com.gikoko.recap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.recap.DownloadsUtil
import com.gikoko.recap.recap.PlanCheckpoint
import com.gikoko.recap.recap.RecapOutput
import com.gikoko.recap.recap.RecapPipeline
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class RecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private var progressValue = 0
    private var currentStep = "Preparing recap..."
    private val logLock = Any()
    private val logBuffer = StringBuilder()

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        val source = inputData.getString(KEY_SOURCE) ?: return Result.failure(errorData("Missing source type"))
        val path = inputData.getString(KEY_PATH).orEmpty()
        val url = inputData.getString(KEY_URL).orEmpty()
        val planPath = inputData.getString(KEY_PLAN_PATH).orEmpty()
        return try {
            publish("Background engine started", "Persistent WorkManager job is running")
            if (source == "queue") return processQueue()

            var cfg = RecapConfigFactory.fromPrefs(applicationContext)
            validateConfiguration(cfg, source)
            val analysisUsesGemini = source != "text" &&
                !(cfg.transcriptMode && cfg.extKey.isNotBlank()) && cfg.provider != "custom"
            val ttsUsesGemini = Prefs.ttsEngine(applicationContext) != "edge"
            if (analysisUsesGemini && cfg.model.contains("flash", ignoreCase = true)) {
                val resolvedModel = GeminiClient.resolveAvailableFlashModel(cfg.apiKey, cfg.model)
                    ?: throw IOException("Gemini API မှာ အသုံးပြုနိုင်သော Flash model မတွေ့ပါ။")
                if (resolvedModel != cfg.model) {
                    appendLog("AUTO MODEL SELECTOR: " + cfg.model + " unavailable; switched to " + resolvedModel)
                    cfg = cfg.copy(model = resolvedModel)
                } else {
                    appendLog("API HEALTH: selected model " + resolvedModel + " supports generateContent")
                }
            } else if ((analysisUsesGemini || ttsUsesGemini) && cfg.apiKey.isNotBlank()) {
                appendLog("API HEALTH: " + GeminiClient.testKey(cfg.apiKey))
            }
            appendLog("USAGE GUARD: " + Prefs.usageLine(applicationContext))

            if (planPath.isEmpty() && cfg.planConfirm && source in setOf("file", "youtube")) {
                val generated = if (source == "file") {
                    RecapPipeline.planFromFile(
                        applicationContext, File(path), cfg,
                        { step -> publish(step, null) },
                        { log -> appendLog(log) },
                        { progress -> publishProgress(progress) }
                    )
                } else {
                    Pair(
                        RecapPipeline.planFromYouTube(
                            applicationContext, url, cfg,
                            { step -> publish(step, null) },
                            { log -> appendLog(log) },
                            { progress -> publishProgress(progress) }
                        ), true
                    )
                }
                if (source == "file") {
                    PlanCheckpoint.save(applicationContext, PlanCheckpoint.keyForFile(File(path), cfg), generated.first, generated.second)
                } else {
                    PlanCheckpoint.save(applicationContext, PlanCheckpoint.keyForYouTube(url, cfg), generated.first, generated.second)
                }
                val pending = File(applicationContext.filesDir, "pending_plan_" + id + ".json")
                pending.writeText(GeminiClient.planToJson(generated.first, generated.second))
                publish("Plan ready — waiting for your confirmation", "AI plan saved locally; opening Plan Editor")
                postCompletionNotification("Recap plan ready", "Tap to review and confirm your plan")
                return Result.success(
                    workDataOf(
                        KEY_NEEDS_CONFIRM to true,
                        KEY_PLAN_PATH to pending.absolutePath,
                        KEY_SOURCE to source,
                        KEY_PATH to path,
                        KEY_URL to url
                    )
                )
            }

            val out = if (planPath.isNotEmpty()) {
                if (!File(planPath).exists()) throw IllegalArgumentException("Confirmed plan file မတွေ့ပါ။")
                val loaded = GeminiClient.planFromJson(File(planPath).readText())
                if (source == "file") {
                    RecapPipeline.renderFromFile(
                        applicationContext, File(path), loaded.first, cfg, loaded.second,
                        { step -> publish(step, null) },
                        { log -> appendLog(log) },
                        { progress -> publishProgress(progress) }
                    )
                } else {
                    RecapPipeline.renderYouTube(
                        applicationContext, loaded.first, cfg,
                        { step -> publish(step, null) },
                        { log -> appendLog(log) },
                        { progress -> publishProgress(progress) }
                    )
                }
            } else when (source) {
                "file" -> RecapPipeline.runFromFile(
                    applicationContext, File(path), cfg,
                    { step -> publish(step, null) },
                    { log -> appendLog(log) },
                    { progress -> publishProgress(progress) }
                )
                "youtube" -> {
                    if (url.isBlank()) throw IllegalArgumentException("YouTube URL မရှိပါ။")
                    RecapPipeline.runFromYouTube(
                        applicationContext, url, cfg,
                        { step -> publish(step, null) },
                        { log -> appendLog(log) },
                        { progress -> publishProgress(progress) }
                    )
                }
                "text" -> runTextRecap(path, cfg)
                else -> throw IllegalArgumentException("Unsupported source: " + source)
            }
            publish("Validating and saving output...", null)
            val archived = archiveOutput(out, source, if (source == "file" || source == "text") path else url)
            if (planPath.isNotEmpty()) runCatching { File(planPath).delete() }
            progressValue = 100
            publish("Completed", "Output validation and archive completed")
            postCompletionNotification("Recap ready", archived.title)
            successData(archived, if (source == "youtube") url else "")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            appendLog("ERROR: " + message)
            if (GeminiClient.isTransientFailure(e) && runAttemptCount < MAX_RETRIES) {
                publish("Temporary service/network error — retry scheduled", message)
                Result.retry()
            } else {
                postCompletionNotification("Recap needs attention", message)
                Result.failure(errorData(message))
            }
        }
    }

    private fun validateConfiguration(cfg: com.gikoko.recap.recap.RecapConfig, source: String) {
        val canAnalyze = when {
            source == "text" -> cfg.extKey.isNotBlank()
            cfg.transcriptMode && cfg.extKey.isNotBlank() -> true
            cfg.provider == "custom" -> cfg.customKey.isNotBlank()
            else -> cfg.apiKey.isNotBlank()
        }
        if (!canAnalyze) {
            val hint = if (source == "text") {
                "Text Recap အတွက် Transcript Provider API Key လိုအပ်ပါသည်။"
            } else if (cfg.provider == "custom") {
                "Custom Provider API Key မရှိပါ။ Settings မှာ ထည့်ပါ။"
            } else {
                "Gemini API Key မရှိပါ။ Settings မှာ ထည့်ပါ။"
            }
            throw IllegalArgumentException(hint)
        }
        if (Prefs.ttsEngine(applicationContext) != "edge" && cfg.apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini TTS အတွက် API Key လိုအပ်ပါသည်။ Edge TTS ကိုရွေးနိုင်သည်။")
        }
    }

    private suspend fun runTextRecap(path: String, cfg: com.gikoko.recap.recap.RecapConfig): RecapOutput {
        val textFile = File(path)
        if (!textFile.exists() || !textFile.isFile) throw IOException("Text source file မတွေ့ပါ။")
        val text = textFile.readText()
        if (text.length < 40) throw IOException("စာသား မလုံလောက်ပါ။ အနည်းဆုံး 40 characters လိုအပ်ပါသည်။")
        val key = PlanCheckpoint.keyForFile(textFile, cfg)
        val cached = PlanCheckpoint.load(applicationContext, key)
        val plan = if (cached != null) {
            appendLog("CHECKPOINT: text recap plan restored; skipping repeated text analysis")
            cached.first
        } else {
            publish("Analyzing source text...", null)
            val generated = GeminiClient.generatePlanTranscript(
                cfg.extBase, cfg.extKey, cfg.extModel, text,
                cfg.targetMinutes, cfg.lang, cfg.fullSegments, cfg.style, cfg.customStyle,
                { log -> appendLog(log) }
            )
            Prefs.bumpUsage(applicationContext, Prefs.KEY_U_EXT, 1)
            PlanCheckpoint.save(applicationContext, key, generated, true)
            generated
        }
        return RecapPipeline.renderYouTube(
            applicationContext, plan, cfg,
            { step -> publish(step, null) },
            { log -> appendLog(log) },
            { progress -> publishProgress(progress) }
        )
    }

    private suspend fun processQueue(): Result {
        val queueFile = File(inputData.getString(KEY_QUEUE_FILE).orEmpty())
        if (!queueFile.exists()) throw IOException("Queue data file မတွေ့ပါ။")
        val paths = JSONArray(queueFile.readText())
        if (paths.length() == 0) throw IOException("Queue ဗလာဖြစ်နေပါသည်။")
        val series = inputData.getBoolean(KEY_QUEUE_SERIES, false)
        val preflightCfg = RecapConfigFactory.fromPrefs(applicationContext, planConfirmOverride = false)
        validateConfiguration(preflightCfg, "file")
        val analysisUsesGemini = !(preflightCfg.transcriptMode && preflightCfg.extKey.isNotBlank()) && preflightCfg.provider != "custom"
        val ttsUsesGemini = Prefs.ttsEngine(applicationContext) != "edge"
        val selectedModel = if (analysisUsesGemini && preflightCfg.model.contains("flash", ignoreCase = true)) {
            GeminiClient.resolveAvailableFlashModel(preflightCfg.apiKey, preflightCfg.model)
                ?: throw IOException("Gemini API မှာ အသုံးပြုနိုင်သော Flash model မတွေ့ပါ။")
        } else {
            if ((analysisUsesGemini || ttsUsesGemini) && preflightCfg.apiKey.isNotBlank()) {
                appendLog("API HEALTH: " + GeminiClient.testKey(preflightCfg.apiKey))
            }
            preflightCfg.model
        }
        if (selectedModel != preflightCfg.model) {
            appendLog("AUTO MODEL SELECTOR: " + preflightCfg.model + " unavailable; switched to " + selectedModel)
        }
        appendLog("USAGE GUARD: " + Prefs.usageLine(applicationContext))
        val stateFile = File(queueFile.parentFile, queueFile.name + ".state.json")
        val state = runCatching { if (stateFile.exists()) JSONObject(stateFile.readText()) else JSONObject() }.getOrDefault(JSONObject())
        val savedItems = state.optJSONObject("items") ?: JSONObject()
        var previousSummary = ""
        var completed = 0
        var failed = 0
        var lastArchived: Archived? = null
        var lastFailure: Exception? = null

        for (i in 0 until paths.length()) {
            val file = File(paths.getString(i))
            val saved = savedItems.optJSONObject(i.toString())
            if (saved?.optBoolean("success", false) == true) {
                val savedVideo = File(saved.optString("video", ""))
                if (savedVideo.exists()) {
                    lastArchived = Archived(
                        savedVideo,
                        File(saved.optString("srt", "")),
                        saved.optString("title", "Recap"),
                        saved.optString("summary", ""),
                        File(saved.optString("thumb", "")),
                        saved.optString("short", "").takeIf { it.isNotEmpty() }?.let { File(it) },
                        saved.optString("podcast", "").takeIf { it.isNotEmpty() }?.let { File(it) }
                    )
                    completed++
                    previousSummary = saved.optString("series_summary", "")
                    appendLog("QUEUE RESUME: reused completed item " + (i + 1) + "/" + paths.length())
                    publishProgress((((i + 1).toDouble() / paths.length()) * 95.0).toInt())
                    continue
                }
            }
            publish("Queue item " + (i + 1) + "/" + paths.length(), "Processing " + file.name)
            val cfg = RecapConfigFactory.fromPrefs(applicationContext, if (series) previousSummary else "", false)
                .copy(model = selectedModel)
            try {
                validateConfiguration(cfg, "file")
                val out = RecapPipeline.runFromFile(
                    applicationContext, file, cfg,
                    { step -> publish("[" + (i + 1) + "/" + paths.length() + "] " + step, null) },
                    { log -> appendLog(log) },
                    { local ->
                        val overall = (((i + local.coerceIn(0, 100) / 100.0) / paths.length()) * 95.0).toInt()
                        publishProgress(overall)
                    }
                )
                val archived = archiveOutput(out, "file", file.absolutePath, forceAutosave = true)
                completed++
                if (series) {
                    var summary = out.summary
                    val chapterIndex = summary.indexOf("\n\nCHAPTERS")
                    if (chapterIndex > 0) summary = summary.substring(0, chapterIndex)
                    if (summary.length > 700) summary = summary.takeLast(700)
                    previousSummary = summary
                }
                val itemState = JSONObject()
                    .put("success", true)
                    .put("video", archived.video.absolutePath)
                    .put("srt", archived.srt.absolutePath)
                    .put("title", archived.title)
                    .put("summary", archived.summary)
                    .put("thumb", if (archived.thumb.exists()) archived.thumb.absolutePath else "")
                    .put("short", archived.shortFile?.absolutePath ?: "")
                    .put("podcast", archived.podcast?.absolutePath ?: "")
                    .put("series_summary", previousSummary)
                savedItems.put(i.toString(), itemState)
                state.put("items", savedItems)
                val tempState = File(stateFile.parentFile, stateFile.name + ".tmp")
                tempState.writeText(state.toString())
                if (stateFile.exists()) stateFile.delete()
                if (!tempState.renameTo(stateFile)) {
                    tempState.copyTo(stateFile, overwrite = true)
                    tempState.delete()
                }
                lastArchived = archived
                appendLog("QUEUE SUCCESS: " + file.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                lastFailure = e
                appendLog("QUEUE ITEM FAILED: " + file.name + " — " + (e.message ?: e.javaClass.simpleName))
                if (series) {
                    appendLog("QUEUE SERIES: pausing after failed chapter so later chapters can resume with correct context")
                    break
                }
            }
        }
        if (completed == 0) throw lastFailure ?: IOException("Queue ထဲက Video တစ်ခုမှ မအောင်မြင်ပါ။")
        val pending = (paths.length() - completed - failed).coerceAtLeast(0)
        if (failed == 0 && pending == 0) {
            queueFile.delete()
            stateFile.delete()
        } else {
            appendLog("QUEUE CHECKPOINT: retained successful item states; retry will skip completed videos")
        }
        val final = lastArchived!!.copy(
            summary = lastArchived!!.summary + "\n\nQUEUE REPORT\nCompleted: " + completed + "/" + paths.length() +
                "\nFailed: " + failed + "\nPending: " + pending
        )
        progressValue = if (pending == 0 && failed == 0) 100 else progressValue
        publish(if (failed == 0 && pending == 0) "Queue completed" else "Queue partially completed",
            "Completed " + completed + "; failed " + failed + "; pending " + pending)
        if (failed > 0 && lastFailure != null && GeminiClient.isTransientFailure(lastFailure!!) && runAttemptCount < MAX_RETRIES) {
            publish("Temporary queue item failure — retry scheduled", lastFailure!!.message)
            return Result.retry()
        }
        postCompletionNotification(
            if (failed == 0 && pending == 0) "Queue completed" else "Queue needs attention",
            "Completed " + completed + " / " + paths.length() + "; failed " + failed + "; pending " + pending
        )
        return successData(final, "")
    }

    private fun archiveOutput(
        out: RecapOutput,
        source: String,
        sourceRef: String,
        forceAutosave: Boolean = false
    ): Archived {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val dir = File(applicationContext.filesDir, "recap_archive/" + stamp)
        if (!dir.mkdirs() && !dir.isDirectory) throw IllegalStateException("Archive folder မဖန်တီးနိုင်ပါ။")
        val video = File(dir, "recap.mp4")
        out.video.copyTo(video, overwrite = true)
        val srt = File(dir, "recap.srt")
        if (out.srt.exists()) out.srt.copyTo(srt, overwrite = true)
        var short: File? = null
        if (out.shorts?.exists() == true) {
            short = File(dir, "short.mp4")
            out.shorts.copyTo(short, overwrite = true)
        }
        var podcast: File? = null
        if (out.podcast?.exists() == true) {
            podcast = File(dir, "podcast.m4a")
            out.podcast.copyTo(podcast, overwrite = true)
        }
        val thumb = File(dir, "thumb.jpg")
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.absolutePath)
                val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    thumb.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                    bitmap.recycle()
                }
            } finally {
                retriever.release()
            }
        }
        val meta = JSONObject()
            .put("title", out.title)
            .put("date", stamp)
            .put("source", sourceRef)
            .put("summary", out.summary)
        File(dir, "meta.json").writeText(meta.toString())
        if (forceAutosave || Prefs.autosave(applicationContext)) {
            DownloadsUtil.saveRecap(applicationContext, video, if (srt.exists()) srt else null)
        }
        return Archived(video, srt, out.title, out.summary, thumb, short, podcast)
    }

    private fun successData(archived: Archived, sourceUrl: String): Result {
        return Result.success(
            workDataOf(
                KEY_VIDEO to archived.video.absolutePath,
                KEY_SRT to archived.srt.absolutePath,
                KEY_TITLE to archived.title.take(200),
                KEY_SUMMARY to archived.summary.take(1500),
                KEY_THUMB to if (archived.thumb.exists()) archived.thumb.absolutePath else "",
                KEY_SHORT to (archived.shortFile?.absolutePath ?: ""),
                KEY_PODCAST to (archived.podcast?.absolutePath ?: ""),
                KEY_SOURCE_URL to sourceUrl
            )
        )
    }

    private fun appendLog(line: String) {
        synchronized(logLock) {
            logBuffer.append(line).append('\n')
            if (logBuffer.length > 1600) logBuffer.delete(0, logBuffer.length - 1600)
            DebugLog.log(applicationContext, "worker: " + line.take(200))
            setProgressAsync(
                workDataOf(
                    KEY_PROGRESS to progressValue,
                    KEY_STEP to currentStep,
                    KEY_LOG to logBuffer.toString()
                )
            )
        }
    }

    private fun publish(step: String, log: String?) {
        currentStep = step
        runCatching { setForegroundAsync(createForegroundInfo()) }
        if (log != null) appendLog(log)
        else setProgressAsync(workDataOf(KEY_PROGRESS to progressValue, KEY_STEP to currentStep, KEY_LOG to synchronized(logLock) { logBuffer.toString() }))
    }

    private fun publishProgress(value: Int) {
        progressValue = value.coerceIn(0, 100)
        setProgressAsync(
            workDataOf(
                KEY_PROGRESS to progressValue,
                KEY_STEP to currentStep,
                KEY_LOG to synchronized(logLock) { logBuffer.toString() }
            )
        )
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message.take(1800))

    private fun postCompletionNotification(title: String, message: String) {
        runCatching {
            val open = Intent(applicationContext, ProcessingActivity::class.java)
                .putExtra(KEY_WORK_NAME, WORK_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                applicationContext, 702, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(title)
                .setContentText(message.take(120))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(COMPLETION_NOTIFICATION_ID, notification)
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recap processing", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = Intent(applicationContext, ProcessingActivity::class.java)
            .putExtra(KEY_WORK_NAME, WORK_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext, 701, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("G.I KO KO MOVIE RECAP")
            .setContentText(currentStep.take(120))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private data class Archived(
        val video: File,
        val srt: File,
        val title: String,
        val summary: String,
        val thumb: File,
        val shortFile: File?,
        val podcast: File?
    )

    companion object {
        const val WORK_NAME = "gikoko_recap_single_active"
        const val KEY_WORK_NAME = "work_name"
        const val KEY_SOURCE = "source"
        const val KEY_PATH = "path"
        const val KEY_URL = "url"
        const val KEY_PLAN_PATH = "plan_path"
        const val KEY_NEEDS_CONFIRM = "needs_confirm"
        const val KEY_QUEUE_FILE = "queue_file"
        const val KEY_JOB_FINGERPRINT = "job_fingerprint"
        const val KEY_QUEUE_SERIES = "queue_series"
        const val KEY_PROGRESS = "progress"
        const val KEY_STEP = "step"
        const val KEY_LOG = "log"
        const val KEY_ERROR = "error"
        const val KEY_VIDEO = "video"
        const val KEY_SRT = "srt"
        const val KEY_TITLE = "title"
        const val KEY_SUMMARY = "summary"
        const val KEY_THUMB = "thumb"
        const val KEY_SHORT = "short"
        const val KEY_PODCAST = "podcast"
        const val KEY_SOURCE_URL = "source_url"
        private const val CHANNEL_ID = "gikoko_recap_worker"
        private const val NOTIFICATION_ID = 7206
        const val COMPLETION_NOTIFICATION_ID = 7207
        private const val MAX_RETRIES = 2

        fun fingerprint(source: String, pathOrUrl: String, planPath: String = ""): String {
            val sourceIdentity = if (source == "file" || source == "text") {
                val file = File(pathOrUrl)
                if (file.exists()) pathOrUrl + "|" + file.length() + "|" + file.lastModified() else pathOrUrl
            } else pathOrUrl
            val raw = source + "|" + sourceIdentity + "|" + planPath
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun queueFingerprint(queueJson: String, series: Boolean = false): String {
            val identity = runCatching {
                val array = JSONArray(queueJson)
                buildString {
                    for (i in 0 until array.length()) {
                        val path = array.getString(i)
                        val file = File(path)
                        append(path).append('|')
                        if (file.exists()) append(file.length()).append('|').append(file.lastModified())
                        append(';')
                    }
                }
            }.getOrDefault(queueJson)
            return fingerprint(if (series) "queue_series" else "queue", identity)
        }

        fun enqueue(context: Context, source: String, pathOrUrl: String, planPath: String = ""): String {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = Data.Builder()
                .putString(KEY_SOURCE, source)
                .putString(KEY_JOB_FINGERPRINT, fingerprint(source, pathOrUrl, planPath))
                .putString(if (source == "file" || source == "text") KEY_PATH else KEY_URL, pathOrUrl)
                .apply { if (planPath.isNotEmpty()) putString(KEY_PLAN_PATH, planPath) }
                .build()
            val request = OneTimeWorkRequestBuilder<RecapWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .addTag("job_" + fingerprint(source, pathOrUrl, planPath))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            return WORK_NAME
        }

        fun enqueueQueue(context: Context, queueJson: String, series: Boolean): String {
            val dir = File(context.filesDir, "queue_jobs").apply { mkdirs() }
            val queueId = queueFingerprint(queueJson, series)
            val queueFile = File(dir, "queue_" + queueId + ".json")
            if (!queueFile.exists()) queueFile.writeText(queueJson)
            dir.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > TimeUnit.DAYS.toMillis(7) }?.forEach { stale ->
                if (stale != queueFile) stale.delete()
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = workDataOf(
                KEY_SOURCE to "queue",
                KEY_JOB_FINGERPRINT to queueFingerprint(queueJson, series),
                KEY_QUEUE_FILE to queueFile.absolutePath,
                KEY_QUEUE_SERIES to series
            )
            val request = OneTimeWorkRequestBuilder<RecapWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .addTag("job_" + queueFingerprint(queueJson, series))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            return WORK_NAME
        }
    }
}
