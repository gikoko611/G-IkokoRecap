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
import com.gikoko.recap.recap.RecapOutput
import com.gikoko.recap.recap.RecapPipeline
import org.json.JSONObject
import java.io.File
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
            val cfg = RecapConfigFactory.fromPrefs(applicationContext)
            publish("Background engine started", "Persistent WorkManager job is running")
            if (planPath.isEmpty() && cfg.planConfirm) {
                val generated = if (source == "file") {
                    RecapPipeline.planFromFile(
                        applicationContext, File(path), cfg,
                        { step -> publish(step, null) },
                        { log -> appendLog(log) },
                        { progress -> publishProgress(progress) }
                    )
                } else if (source == "youtube") {
                    Pair(
                        RecapPipeline.planFromYouTube(
                            applicationContext, url, cfg,
                            { step -> publish(step, null) },
                            { log -> appendLog(log) },
                            { progress -> publishProgress(progress) }
                        ), true
                    )
                } else {
                    throw IllegalArgumentException("Unsupported source: " + source)
                }
                if (source == "file") {
                    PlanCheckpoint.save(applicationContext, PlanCheckpoint.keyForFile(File(path), cfg), generated.first, generated.second)
                } else {
                    PlanCheckpoint.save(applicationContext, PlanCheckpoint.keyForYouTube(url, cfg), generated.first, generated.second)
                }
                val pending = File(applicationContext.filesDir, "pending_plan_" + id + ".json")
                pending.writeText(GeminiClient.planToJson(generated.first, generated.second))
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
                else -> throw IllegalArgumentException("Unsupported source: " + source)
            }
            publish("Validating and saving output...", null)
            val archived = archiveOutput(out, source, if (source == "file") path else url)
            progressValue = 100
            publish("Completed", "Output validation and archive completed")
            Result.success(
                workDataOf(
                    KEY_VIDEO to archived.video.absolutePath,
                    KEY_SRT to archived.srt.absolutePath,
                    KEY_TITLE to archived.title,
                    KEY_SUMMARY to archived.summary.take(3500),
                    KEY_THUMB to archived.thumb.absolutePath,
                    KEY_SHORT to (archived.shortFile?.absolutePath ?: ""),
                    KEY_PODCAST to (archived.podcast?.absolutePath ?: ""),
                    KEY_SOURCE_URL to if (source == "youtube") url else ""
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            appendLog("ERROR: " + message)
            if (GeminiClient.isTransientFailure(e) && runAttemptCount < MAX_RETRIES) {
                publish("Temporary service/network error — retry scheduled", message)
                Result.retry()
            } else {
                Result.failure(errorData(message))
            }
        }
    }

    private fun archiveOutput(out: RecapOutput, source: String, sourceRef: String): Archived {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
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
            retriever.setDataSource(video.absolutePath)
            val bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                thumb.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                bitmap.recycle()
            }
            retriever.release()
        }
        val meta = JSONObject()
            .put("title", out.title)
            .put("date", stamp)
            .put("source", sourceRef)
            .put("summary", out.summary)
        File(dir, "meta.json").writeText(meta.toString())
        if (Prefs.autosave(applicationContext)) {
            DownloadsUtil.saveRecap(applicationContext, video, if (srt.exists()) srt else null)
        }
        return Archived(video, srt, out.title, out.summary, thumb, short, podcast)
    }

    private fun appendLog(line: String) {
        synchronized(logLock) {
            logBuffer.append(line).append('\n')
            if (logBuffer.length > 5000) logBuffer.delete(0, logBuffer.length - 5000)
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

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

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
            .setContentText(currentStep)
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
        private const val MAX_RETRIES = 2

        fun enqueue(context: Context, source: String, pathOrUrl: String, planPath: String = ""): String {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = Data.Builder()
                .putString(KEY_SOURCE, source)
                .putString(if (source == "file") KEY_PATH else KEY_URL, pathOrUrl)
                .apply { if (planPath.isNotEmpty()) putString(KEY_PLAN_PATH, planPath) }
                .build()
            val request = OneTimeWorkRequestBuilder<RecapWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            return WORK_NAME
        }
    }
}
