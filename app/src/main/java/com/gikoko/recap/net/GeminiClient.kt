package com.gikoko.recap.net

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okio.BufferedSink

data class Segment(val startSec: Int, val endSec: Int, val text: String)
data class RecapPlan(val title: String, val segments: List<Segment>, val chapters: List<String> = emptyList(), val quiz: List<String> = emptyList())

object GeminiClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private const val BASE = "https://generativelanguage.googleapis.com"
    const val TTS_MODEL = "gemini-3.8-flash-tts"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.MINUTES)
        .callTimeout(12, TimeUnit.MINUTES)
        .build()

    fun normalizeBase(url: String): String {
        var u = url.trim()
        while (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }
        return u
    }

    fun isYouTubeLink(url: String): Boolean {
        val pattern = Regex("^(https?://)?(www\\.)?(m\\.)?(youtube\\.com|youtu\\.be)/.+")
        return pattern.matches(url.trim())
    }

    fun guessMime(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".mp4") -> "video/mp4"
            n.endsWith(".mov") -> "video/quicktime"
            n.endsWith(".mkv") -> "video/x-matroska"
            n.endsWith(".webm") -> "video/webm"
            n.endsWith(".avi") -> "video/avi"
            n.endsWith(".3gp") -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    fun testKey(apiKey: String): String {
        val req = Request.Builder().url(BASE + "/v1beta/models?key=" + apiKey).get().build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(300))
            val arr = JSONObject(txt).optJSONArray("models") ?: JSONArray()
            return "OK - models: " + arr.length()
        }
    }

    fun testKeyCustom(baseUrl: String, apiKey: String): String {
        val req = Request.Builder()
            .url(normalizeBase(baseUrl) + "/models")
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(300))
            val arr = JSONObject(txt).optJSONArray("data") ?: JSONArray()
            return "OK - total models: " + arr.length()
        }
    }

    fun listCustomModels(baseUrl: String, apiKey: String): ArrayList<String> {
        val builder = Request.Builder().url(normalizeBase(baseUrl) + "/models").get()
        if (apiKey.isNotEmpty()) builder.header("Authorization", "Bearer " + apiKey)
        val req = builder.build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(600))
            val arr = JSONObject(txt).optJSONArray("data") ?: JSONArray()
            val free = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id", "")
                if (id.isEmpty()) continue
                val pricing = o.optJSONObject("pricing")
                val promptFree = pricing != null && pricing.optString("prompt", "1") == "0"
                val compFree = pricing != null && pricing.optString("completion", "1") == "0"
                if (id.endsWith(":free") || (promptFree && compFree)) free.add(id)
            }
            free.sort()
            if (free.isEmpty()) throw IOException("Free model တစ်ခုမှ မတွေ့ပါ - provider မှာ free model မရှိနိုင်ပါ")
            return free
        }
    }

    private class ApiHttpException(val statusCode: Int, message: String) : IOException(message)

    private const val MAX_RETRIES = 3

    private fun isRetryableStatus(code: Int): Boolean =
        code == 408 || code == 425 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504

    private fun retryDelayMs(attempt: Int, retryAfter: String? = null): Long {
        val serverDelay = retryAfter?.trim()?.toLongOrNull()?.let { it.coerceIn(1L, 60L) * 1000L }
        if (serverDelay != null) return serverDelay
        val base = when (attempt) {
            0 -> 2000L
            1 -> 5000L
            else -> 12000L
        }
        return base + java.util.concurrent.ThreadLocalRandom.current().nextLong(250L, 1251L)
    }

    private fun executeJson(req: Request, label: String): JSONObject {
        var retries = 0
        while (true) {
            try {
                val retryDelay = client.newCall(req).execute().use { resp ->
                    val txt = resp.body?.string() ?: ""
                    if (resp.isSuccessful) return JSONObject(txt)
                    if (!isRetryableStatus(resp.code) || retries >= MAX_RETRIES) {
                        throw ApiHttpException(resp.code, label + " HTTP " + resp.code + ": " + txt.take(600))
                    }
                    retryDelayMs(retries, resp.header("Retry-After"))
                }
                retries++
                val seconds = retryDelay / 1000.0
                Thread.sleep(retryDelay)
            } catch (e: ApiHttpException) {
                throw e
            } catch (e: IOException) {
                if (retries >= MAX_RETRIES) {
                    throw IOException(label + " network failure after " + (retries + 1) + " attempts: " + (e.message ?: e.javaClass.simpleName), e)
                }
                val delay = retryDelayMs(retries)
                retries++
                Thread.sleep(delay)
            }
        }
    }

    fun isTransientFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is ApiHttpException && isRetryableStatus(current.statusCode)) return true
            if (current is java.net.SocketTimeoutException ||
                current is java.net.UnknownHostException ||
                current is java.net.ConnectException ||
                current is java.net.SocketException) return true
            val message = current.message.orEmpty().lowercase()
            if (message.contains("http 408") || message.contains("http 425") ||
                message.contains("http 429") || message.contains("http 500") ||
                message.contains("http 502") || message.contains("http 503") ||
                message.contains("http 504") || message.contains("timeout") ||
                message.contains("gemini video processing 10") ||
                message.contains("connection reset") || message.contains("unable to resolve host")) return true
            current = current.cause
        }
        return false
    }

    private fun httpStatus(error: Throwable): Int? {
        var current: Throwable? = error
        while (current != null) {
            if (current is ApiHttpException) return current.statusCode
            val match = Regex("HTTP\\s+(\\d{3})", RegexOption.IGNORE_CASE).find(current.message.orEmpty())
            if (match != null) return match.groupValues[1].toIntOrNull()
            current = current.cause
        }
        return null
    }

    fun resolveAvailableFlashModel(apiKey: String, currentModel: String, excludeCurrent: Boolean = false): String? {
        val models = get(apiKey, "/v1beta/models").optJSONArray("models") ?: JSONArray()
        val available = HashSet<String>()
        for (i in 0 until models.length()) {
            val item = models.optJSONObject(i) ?: continue
            val name = item.optString("name", "").removePrefix("models/")
            val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            val supportsGenerate = (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            if (supportsGenerate && name.startsWith("gemini-") &&
                name.contains("flash") && !name.contains("tts") &&
                !name.contains("image") && !name.contains("live") &&
                !name.contains("transcribe")) {
                available.add(name)
            }
        }
        if (!excludeCurrent && currentModel in available) return currentModel
        val candidates = if (excludeCurrent) available.filter { it != currentModel }.toSet() else available
        val preference = listOf(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
        )
        return preference.firstOrNull { it in candidates } ?: candidates.sorted().firstOrNull()
    }

    private fun findAvailableFlashFallback(apiKey: String, currentModel: String): String? =
        resolveAvailableFlashModel(apiKey, currentModel, excludeCurrent = true)?.takeIf { it != currentModel }

    private fun post(apiKey: String, path: String, body: String): JSONObject {
        val req = Request.Builder()
            .url(BASE + path + "?key=" + apiKey)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        return executeJson(req, "Gemini request")
    }

    private fun get(apiKey: String, path: String): JSONObject {
        val req = Request.Builder().url(BASE + path + "?key=" + apiKey).get().build()
        return executeJson(req, "Gemini GET")
    }

    private data class UploadQuery(val offset: Long, val fileName: String, val finalized: Boolean)

    private fun uploadStateFile(context: Context, file: File, mime: String, displayName: String): File {
        val identity = file.absolutePath + "|" + file.length() + "|" + file.lastModified() + "|" + mime + "|" + displayName
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val dir = File(context.noBackupFilesDir, "gemini_upload_sessions").apply { mkdirs() }
        return File(dir, hash + ".json")
    }

    private fun saveUploadState(stateFile: File?, uploadUrl: String, file: File, offset: Long) {
        if (stateFile == null) return
        runCatching {
            val state = JSONObject()
                .put("uploadUrl", uploadUrl)
                .put("fileLength", file.length())
                .put("lastModified", file.lastModified())
                .put("offset", offset)
                .put("savedAt", System.currentTimeMillis())
            val temp = File(stateFile.parentFile, stateFile.name + ".tmp")
            temp.writeText(state.toString())
            if (stateFile.exists()) stateFile.delete()
            if (!temp.renameTo(stateFile)) {
                temp.copyTo(stateFile, overwrite = true)
                temp.delete()
            }
        }
    }

    private fun queryUploadSession(uploadUrl: String, fallbackOffset: Long = 0L): UploadQuery? {
        val req = Request.Builder()
            .url(uploadUrl)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "query")
            .post(ByteArray(0).toRequestBody("application/octet-stream".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (resp.code == 400 || resp.code == 404 || resp.code == 410) return null
            if (!resp.isSuccessful) {
                throw IOException("Upload session query failed HTTP " + resp.code + ": " + txt.take(300))
            }
            val root = runCatching { JSONObject(txt) }.getOrNull()
            val fileObj = root?.optJSONObject("file") ?: root
            val fileName = fileObj?.optString("name", "").orEmpty()
            val status = resp.header("X-Goog-Upload-Status").orEmpty().lowercase()
            val received = resp.header("X-Goog-Upload-Size-Received")?.toLongOrNull()
                ?: root?.optString("sizeReceived", "")?.toLongOrNull()
                ?: fallbackOffset
            return UploadQuery(received, fileName, status == "final" || fileName.isNotEmpty())
        }
    }

    fun uploadVideo(
        apiKey: String,
        file: File,
        mime: String,
        displayName: String,
        onLog: (String) -> Unit = {},
        context: Context? = null
    ): String {
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            throw IOException("Video file မရှိပါ သို့မဟုတ် empty ဖြစ်နေသည်")
        }
        val totalBytes = file.length()
        onLog("Upload preparing: " + String.format(java.util.Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0)))
        val stateFile = context?.let { uploadStateFile(it, file, mime, displayName) }
        var uploadUrl = ""
        var offset = 0L
        var fileName = ""

        if (stateFile != null && stateFile.exists()) {
            val saved = runCatching { JSONObject(stateFile.readText()) }.getOrNull()
            val matches = saved != null &&
                saved.optLong("fileLength", -1L) == totalBytes &&
                saved.optLong("lastModified", -1L) == file.lastModified()
            if (matches) {
                val candidate = saved?.optString("uploadUrl", "").orEmpty()
                if (candidate.isNotEmpty()) {
                    val status = queryUploadSession(candidate, saved?.optLong("offset", 0L) ?: 0L)
                    if (status != null) {
                        uploadUrl = candidate
                        offset = status.offset.coerceIn(0L, totalBytes)
                        fileName = status.fileName
                        if (status.finalized && fileName.isNotEmpty()) {
                            stateFile.delete()
                            onLog("UPLOAD RESUME: server confirms upload already finalized")
                            return fileName
                        }
                        saveUploadState(stateFile, uploadUrl, file, offset)
                        onLog("UPLOAD RESUME: continuing from acknowledged " +
                            String.format(java.util.Locale.US, "%.1f MB", offset / (1024.0 * 1024.0)))
                    } else {
                        stateFile.delete()
                        onLog("UPLOAD RESUME: old session expired; starting a fresh session")
                    }
                } else {
                    stateFile.delete()
                }
            } else {
                stateFile.delete()
            }
        }

        if (uploadUrl.isEmpty()) {
            val meta = JSONObject().put("file", JSONObject().put("display_name", displayName)).toString()
            val startReq = Request.Builder()
                .url(BASE + "/upload/v1beta/files?key=" + apiKey)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", totalBytes.toString())
                .header("X-Goog-Upload-Header-Content-Type", mime)
                .post(meta.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(startReq).execute().use { resp ->
                val txt = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw IOException("Upload start failed HTTP " + resp.code + ": " + txt.take(400))
                uploadUrl = resp.header("X-Goog-Upload-URL")
                    ?: throw IOException("No upload URL returned")
            }
            saveUploadState(stateFile, uploadUrl, file, 0L)
        }

        val minChunkSize = 100 * 1024
        val maxChunkSize = 1024 * 1024
        var chunkSize = 256 * 1024
        var chunkNumber = 0
        java.io.RandomAccessFile(file, "r").use { raf ->
            while (offset < totalBytes) {
                val count = minOf(chunkSize.toLong(), totalBytes - offset).toInt()
                val bytes = ByteArray(count)
                raf.seek(offset)
                raf.readFully(bytes)
                val startOffset = offset
                val isLast = startOffset + count >= totalBytes
                val command = if (isLast) "upload, finalize" else "upload"
                var acceptedOffset: Long? = null
                var attempts = 0
                while (acceptedOffset == null) {
                    val chunkReq = Request.Builder()
                        .url(uploadUrl)
                        .header("Content-Length", count.toString())
                        .header("X-Goog-Upload-Offset", startOffset.toString())
                        .header("X-Goog-Upload-Command", command)
                        .post(bytes.toRequestBody(mime.toMediaType()))
                        .build()
                    val chunkStartedAt = System.nanoTime()
                    try {
                        client.newCall(chunkReq).execute().use { resp ->
                            val txt = resp.body?.string() ?: ""
                            if (!resp.isSuccessful) {
                                throw IOException("Gemini upload chunk " + (chunkNumber + 1) + " failed HTTP " + resp.code + ": " + txt.take(400))
                            }
                            if (isLast) {
                                val root = JSONObject(txt)
                                val fileObj = root.optJSONObject("file") ?: root
                                fileName = fileObj.optString("name", "")
                                if (fileName.isEmpty()) throw IOException("Upload response missing file name: " + txt.take(200))
                            }
                        }
                        acceptedOffset = startOffset + count
                    } catch (e: IOException) {
                        val serverState = runCatching { queryUploadSession(uploadUrl, startOffset) }.getOrNull()
                        if (serverState != null) {
                            if (serverState.finalized && serverState.fileName.isNotEmpty()) {
                                fileName = serverState.fileName
                                acceptedOffset = totalBytes
                            } else if (serverState.offset > startOffset) {
                                acceptedOffset = serverState.offset.coerceAtMost(totalBytes)
                            }
                        }
                        if (acceptedOffset == null) {
                            attempts++
                            if (attempts > 3 || !isTransientFailure(e)) throw e
                            val delay = retryDelayMs(attempts - 1)
                            onLog("UPLOAD RETRY: chunk " + (chunkNumber + 1) + " after " + (delay / 1000.0) + "s")
                            Thread.sleep(delay)
                        }
                    }
                    if (acceptedOffset != null) {
                        val elapsedMs = ((System.nanoTime() - chunkStartedAt) / 1_000_000L).coerceAtLeast(1L)
                        offset = acceptedOffset!!.coerceIn(0L, totalBytes)
                        chunkNumber++
                        if (elapsedMs < 2000L && chunkSize < maxChunkSize) {
                            chunkSize = minOf(chunkSize + 100 * 1024, maxChunkSize)
                        } else if (elapsedMs > 8000L && chunkSize > minChunkSize) {
                            chunkSize = maxOf(chunkSize - 100 * 1024, minChunkSize)
                        }
                        saveUploadState(stateFile, uploadUrl, file, offset)
                        val percent = ((offset * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        onLog("Upload acknowledged: " + percent + "% (" +
                            String.format(java.util.Locale.US, "%.1f / %.1f MB", offset / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0)) +
                            ") - chunk " + chunkNumber + ", next " + (chunkSize / 1024) + " KB")
                    }
                }
                if (fileName.isNotEmpty()) break
            }
        }

        if (fileName.isEmpty()) {
            val finalizeReq = Request.Builder()
                .url(uploadUrl)
                .header("Content-Length", "0")
                .header("X-Goog-Upload-Offset", offset.toString())
                .header("X-Goog-Upload-Command", "upload, finalize")
                .post(ByteArray(0).toRequestBody(mime.toMediaType()))
                .build()
            client.newCall(finalizeReq).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("Gemini upload finalize failed HTTP " + resp.code + ": " + txt.take(400))
                val root = JSONObject(txt)
                fileName = (root.optJSONObject("file") ?: root).optString("name", "")
            }
        }
        if (fileName.isEmpty()) throw IOException("Upload completed but Gemini file name was not returned")
        stateFile?.delete()
        onLog("UPLOAD COMPLETE: " + fileName)
        return fileName
    }

    fun waitForFileReady(apiKey: String, fileName: String, onLog: (String) -> Unit): String {
        val startedAt = System.nanoTime()
        val timeoutNanos = TimeUnit.MINUTES.toNanos(10)
        while (System.nanoTime() - startedAt < timeoutNanos) {
            val j = get(apiKey, "/v1beta/" + fileName)
            val fo = j.optJSONObject("file") ?: j
            val state = fo.optString("state", "STATE_UNSPECIFIED")
            val elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt)
            onLog("Gemini processing: " + state + " (" + elapsed + "s)")
            if (state == "ACTIVE") {
                var u = fo.optString("uri", "")
                if (u.isEmpty()) u = BASE + "/v1beta/" + fileName
                onLog("Video file ACTIVE - analysis စတင်နိုင်ပါပြီ")
                return u
            }
            if (state == "FAILED") {
                throw IOException("Gemini က video processing ကို FAILED ပြန်ပေးသည်။ Video format/codec ကို စစ်ဆေးပါ။")
            }
            Thread.sleep(5000)
        }
        throw IOException("Gemini video processing 10 မိနစ်ကျော်ကြာနေသည်။ Upload ပြီးပါပြီ (" + fileName + ")၊ ထပ်စမ်းမီ network နှင့် Gemini File status ကို စစ်ဆေးပါ။")
    }

    private fun styleBrief(style: String, lang: String): String {
        val my = lang != "en"
        return when (style) {
            "talk" -> if (my) "ပရိသတ်နဲ့ တိုက်ရိုက် စကားပြောတုန်းပုံစံနဲ့ ခင်မင်စွာ ရှင်းပြပါ - သူငယ်ချင်းတစ်ယောက်ကို ပြောသလို ရင်းနှီးတဲ့ အသုံးအနှုန်းသုံးပါ။" else "Talk directly to the viewer like a friendly narrator explaining to a friend, casual conversational tone."
            "tech" -> if (my) "Video ထဲက နည်းပညာဆိုင်ရာ အချက်တွေကို အလေးပေးပြီး ဆရာသင်ပုံစံနဲ့ အဆင့်ဆင့် ရှင်းပြပါ - ဘာတွေဘယ်လိုအလုပ်လုပ်လဲ အသေးစိတ်ထည့်ပါ။" else "Focus on the technical aspects and explain step by step like a teacher, include how things work in detail."
            "story" -> if (my) "ဇာတ်လမ်းတစ်ခုလုံးကို ဇာတ်လမ်းပြောသမားလို သုံးသပ်ပြောပြပါ - ဇာတ်လမ်းအညွှန်း၊ ဇာတ်ကောင်တွေ၊ အကြောင်းအရင်းအကျိုးအပြား၊ ဇာတ်လမ်းက ပေးချင်တဲ့ သဝိသေသ အပြည့်အစုံပါဝါ။" else "Analyze the whole story like a storyteller: plot, characters, cause and effect, and the message of the story."
            "highlights" -> if (my) "အဓိကအချက်ကျတဲ့ အချက်တွေပဲ ရွေးပြီး တိုတိုလိုက် ဖြတ်တောက်ပြောပါ - မလိုအပ်တဲ့ အချက်တွေ ချန်လှုပါ။" else "Focus only on the key takeaways, crisp and punchy, skip anything non-essential."
            "news" -> if (my) "သတင်းထောက်တစ်ယောက်လို တရားဝင်တဲ့ သတင်းစာပိန်းနဲ့ ဖြစ်ရပ်တွေကို တင်ပြပါ - ဖြစ်စဉ်အစဉ်အလာအတိုင်း သတင်းပုံစံနဲ့ ရေးပါ။" else "Present like a professional news anchor reporting the events, formal and informative."
            "funny" -> if (my) "ဟာသဆန်တဲ့ မှတ်ချက်တွေနဲ့ ပေါ့ပါးစွာ ပြောပေမယ့် အဓိကအချက်တွေ အပြည့်အစုံ ထည့်ပြောပါ။" else "Use light humor and playful commentary while still covering the main content."
            "eli5" -> if (my) "နေ့စဉ်သုံး လွယ်လွယ်တဲ့ စကားလုံးတွေနဲ့ ဥပမာသာဓကပေးပြီး လူမသိသူမသာ နားလည်နိုင်တဲ့ ပုံစံနဲ့ ရိုးရှင်းစွာ ရှင်းပြပါ - ခက်ခဲတဲ့ နည်းပညာစကားလုံးတွေ ရှောင်ပါ။" else "Explain in very simple everyday words with easy analogies so anyone can understand, avoid technical jargon."
            "lesson" -> if (my) "သင်ခန်းစာပုံစံနဲ့ တင်ပြပါ - ဒီ video ကနေ ဘာတွေ သင်ယူနိုင်မလဲ၊ အချက်ကျကျ insight တွေနဲ့ လက်တွေ့သုံးနိုင်တဲ့ သင်ခန်းစာတွေ ထုတ်ပြောပါ။" else "Present as a lesson with key insights and practical takeaways that can be learned from this video."
            "trailer" -> if (my) "ဇာတ်လမ်းကြော်ငြာ (trailer) လို စိတ်လှုပ်စရာကောင်းတဲ့ ပုံစံနဲ့ တင်ပြပါ - စပ်ဆန်းမှုရှိရှိ၊ ဒိုင်းမားမားတဲ့ စကားလုံးတွေသုံးပြီး အနှစ်ချုပ် မပြောသေးဘဲ စိတ်ဝင်စားမှု လှုံ့ဆော်ပါ။" else "Write like an exciting movie trailer teaser, dramatic and suspenseful, build curiosity without spoiling everything."
            else -> if (my) "Video တစ်ခုလုံးရဲ့ အဓိကအချက်တွေကို ခြုံငုံပြီး ရှင်းလင်းစွာ ပြောပြပါ။" else "Give a clear overall summary of the video."
        }
    }

    private fun planPrompt(targetMinutes: Int, lang: String, full: Boolean, style: String, custom: String): String {
        val targetTxt = if (targetMinutes == -1) "the ideal length for this content (you decide, between 1 and 5 minutes)" else if (targetMinutes <= 0) "about 3 minutes" else ("about " + targetMinutes + " minutes")
        val segTxt = if (full) "6 to 12 segments" else "4 to 6 segments"
        val styleTxt = styleBrief(style, lang)
        val customTxt = if (custom.isNotBlank()) ("Additional narration style instructions from the user (must follow): " + custom.trim() + " ") else ""
        return if (lang == "en") {
            "You are a video recap editor. The video is provided below. " +
                "Create a short recap narration plan in ENGLISH language. " +
                "The narration, when spoken, should total " + targetTxt + ". " +
                "Pick the most important moments of the video. " +
                "Narration style: " + styleTxt + " " + customTxt +
                "Return STRICT JSON only, in this exact shape: " +
                "{\"title\":\"short title in English\",\"segments\":[{\"startSec\":123,\"endSec\":456,\"text\":\"English narration text\"}],\"chapters\":[{\"t\":\"00:12\",\"title\":\"chapter title\"}],\"quiz\":[{\"q\":\"question about the video\",\"a\":\"answer\"}]} " +
                "Use " + segTxt + ". Each segment text must be one or two English sentences that summarize that part of the video. " +
                "Each segment must be between 15 and 90 seconds long. Also include a \"chapters\" array (4 to 8 entries: t = mm:ss timestamp of that moment in the original video, title = short chapter title) and a \"quiz\" array (3 to 5 question/answer pairs about the video). No markdown, JSON only."
        } else {
            "You are a video recap editor. The video is provided below. " +
                "Create a short recap narration plan in Myanmar (Burmese) language. " +
                "The narration, when spoken, should total " + targetTxt + ". " +
                "Pick the most important moments of the video. " +
                "Narration style: " + styleTxt + " " + customTxt +
                "Return STRICT JSON only, in this exact shape: " +
                "{\"title\":\"short title in Myanmar\",\"segments\":[{\"startSec\":123,\"endSec\":456,\"text\":\"Myanmar narration text\"}],\"chapters\":[{\"t\":\"00:12\",\"title\":\"chapter title\"}],\"quiz\":[{\"q\":\"question about the video\",\"a\":\"answer\"}]} " +
                "Use " + segTxt + ". Each segment text must be one or two Myanmar sentences that summarize that part of the video. " +
                "Each segment must be between 15 and 90 seconds long. Also include a \"chapters\" array (4 to 8 entries: t = mm:ss timestamp of that moment in the original video, title = short chapter title) and a \"quiz\" array (3 to 5 question/answer pairs about the video). No markdown, JSON only."
        }
    }

    fun generatePlan(
        apiKey: String,
        model: String,
        videoUri: String,
        targetMinutes: Int,
        lang: String,
        full: Boolean,
        style: String,
        custom: String,
        onLog: (String) -> Unit
    ): RecapPlan {
        val prompt = planPrompt(targetMinutes, lang, full, style, custom)
        val textPart = JSONObject().put("text", prompt)
        val filePart = JSONObject().put("file_data", JSONObject().put("file_uri", videoUri))
        val content = JSONObject().put("parts", JSONArray().put(textPart).put(filePart))
        val body = JSONObject()
            .put("contents", JSONArray().put(content))
            .put("generationConfig", JSONObject().put("temperature", 0.4).put("response_mime_type", "application/json"))
        val requestBody = body.toString()
        val resp = try {
            post(apiKey, "/v1beta/models/" + model + ":generateContent", requestBody)
        } catch (e: IOException) {
            if (httpStatus(e) != 503) throw e
            val fallbackModel = findAvailableFlashFallback(apiKey, model) ?: throw e
            onLog("MODEL FAILOVER: " + model + " is overloaded; trying API-listed model " + fallbackModel)
            post(apiKey, "/v1beta/models/" + fallbackModel + ":generateContent", requestBody)
        }
        val txt = resp.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        onLog("Plan: " + txt.take(300))
        return parsePlan(txt)
    }

    fun generatePlanCustom(
        baseUrl: String,
        apiKey: String,
        model: String,
        videoRef: String,
        isDataUrl: Boolean,
        targetMinutes: Int,
        lang: String,
        full: Boolean,
        style: String,
        custom: String,
        onLog: (String) -> Unit
    ): RecapPlan {
        val prompt = planPrompt(targetMinutes, lang, full, style, custom)
        val fileObj = JSONObject().put("filename", "video.mp4")
        if (isDataUrl) {
            fileObj.put("file_data", videoRef)
        } else {
            fileObj.put("file_url", videoRef)
        }
        val contentPart = JSONObject().put("type", "file").put("file", fileObj)
        val textPart = JSONObject().put("type", "text").put("text", prompt)
        val msg = JSONObject().put("role", "user")
            .put("content", JSONArray().put(textPart).put(contentPart))
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(msg))
        val req = Request.Builder()
            .url(normalizeBase(baseUrl) + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        var attempt = 0
        while (true) {
            client.newCall(req).execute().use { resp ->
                val txt = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val retriable = resp.code >= 500 || resp.code == 429
                    if (!retriable || attempt >= 2) throw IOException("HTTP " + resp.code + ": " + txt.take(400))
                    attempt++
                    return@use
                }
                val j = JSONObject(txt)
                val choices = j.optJSONArray("choices")
                    ?: throw IOException("No choices in response: " + txt.take(200))
                val content = choices.getJSONObject(0).getJSONObject("message").optString("content", "")
                onLog("Plan: " + content.take(300))
                return parsePlan(content)
            }
            Thread.sleep(3000L * attempt)
        }
    }

    fun generatePlanTranscript(
        baseUrl: String,
        apiKey: String,
        model: String,
        transcript: String,
        targetMinutes: Int,
        lang: String,
        full: Boolean,
        style: String,
        custom: String,
        onLog: (String) -> Unit
    ): RecapPlan {
        val base = planPrompt(targetMinutes, lang, full, style, custom)
        val prompt = base.replace("The video is provided below. ", "The video transcript with [mm:ss] timestamps is provided below as plain text. ") + "\n\nTRANSCRIPT:\n" + transcript
        val msg = JSONObject().put("role", "user").put("content", prompt)
        val body = JSONObject().put("model", model).put("messages", JSONArray().put(msg))
        val req = Request.Builder()
            .url(normalizeBase(baseUrl) + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        var attempt = 0
        while (true) {
            client.newCall(req).execute().use { resp ->
                val txt = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val retriable = resp.code >= 500 || resp.code == 429
                    if (!retriable || attempt >= 2) throw IOException("HTTP " + resp.code + ": " + txt.take(400))
                    attempt++
                    return@use
                }
                val j = JSONObject(txt)
                val choices = j.optJSONArray("choices") ?: throw IOException("No choices in response: " + txt.take(200))
                val content = choices.getJSONObject(0).getJSONObject("message").optString("content", "")
                onLog("Plan: " + content.take(300))
                return parsePlan(content)
            }
            Thread.sleep(3000L * attempt)
        }
    }

    fun voiceMatch(apiKey: String, model: String, sample: File, onLog: (String) -> Unit): Pair<String, String> {
        val mime = guessMime(sample.name)
        val fileName = uploadVideo(apiKey, sample, mime, sample.name)
        val ready = waitForFileReady(apiKey, fileName, onLog)
        val voices = "Sulafat, Vindemiatrix, Erinome, Aoede, Kore, Leda, Zephyr, Despina, Charon, Iapetus, Achird, Orus, Puck, Fenrir, Algenib, Rasalgethi"
        val prompt = "Listen to this short voice sample. From this exact list of prebuilt voices, pick the ONE that is closest in gender, pitch, tone and speaking style: " + voices + ". " +
            "Also write one short English sentence of narration style instructions to imitate the sample speaker's delivery (energy, pace, formality). " +
            "Return STRICT JSON only: {\"voice\":\"ChosenVoice\",\"style\":\"style instruction\"}"
        val textPart = JSONObject().put("text", prompt)
        val filePart = JSONObject().put("file_data", JSONObject().put("file_uri", ready))
        val content = JSONObject().put("parts", JSONArray().put(textPart).put(filePart))
        val body = JSONObject()
            .put("contents", JSONArray().put(content))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("response_mime_type", "application/json"))
        val resp = post(apiKey, "/v1beta/models/" + model + ":generateContent", body.toString())
        val txt = resp.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        onLog("VoiceMatch: " + txt.take(200))
        val start = txt.indexOf('{')
        val end = txt.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("Voice match response not JSON: " + txt.take(200))
        val j = JSONObject(txt.substring(start, end + 1))
        var voice = j.optString("voice", "Sulafat")
        val style = j.optString("style", "")
        if (!voices.contains(voice)) voice = "Sulafat"
        return Pair(voice, style)
    }

    fun whisperTranscribe(baseUrl: String, apiKey: String, audio: File): String {
        val mp = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "text")
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val req = Request.Builder()
            .url(normalizeBase(baseUrl) + "/audio/transcriptions")
            .header("Authorization", "Bearer " + apiKey)
            .post(mp)
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(400))
            if (txt.isBlank()) throw IOException("Whisper returned empty transcript")
            return txt.trim()
        }
    }

    fun planToJson(plan: RecapPlan, useFrames: Boolean): String {
        val segs = JSONArray()
        for (s in plan.segments) {
            segs.put(JSONObject().put("start", s.startSec).put("end", s.endSec).put("text", s.text))
        }
        return JSONObject()
            .put("title", plan.title)
            .put("segments", segs)
            .put("chapters", JSONArray(plan.chapters))
            .put("quiz", JSONArray(plan.quiz))
            .put("frames", useFrames)
            .toString()
    }

    fun planFromJson(raw: String): Pair<RecapPlan, Boolean> {
        val j = JSONObject(raw.trim())
        val segs = ArrayList<Segment>()
        val arr = j.optJSONArray("segments") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            segs.add(Segment(o.optInt("start", 0), o.optInt("end", 0), o.optString("text", "")))
        }
        val chapters = ArrayList<String>()
        val ch = j.optJSONArray("chapters")
        if (ch != null) for (i in 0 until ch.length()) chapters.add(ch.optString(i))
        val quiz = ArrayList<String>()
        val qz = j.optJSONArray("quiz")
        if (qz != null) for (i in 0 until qz.length()) quiz.add(qz.optString(i))
        return Pair(RecapPlan(j.optString("title", "Video Recap"), segs, chapters, quiz), j.optBoolean("frames", true))
    }

    fun parsePlan(raw: String): RecapPlan {
        var s = raw.trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("AI response not JSON: " + raw.take(200))
        s = s.substring(start, end + 1)
        val j = JSONObject(s)
        val title = j.optString("title", "Video Recap")
        val arr = j.optJSONArray("segments") ?: JSONArray()
        val segs = ArrayList<Segment>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val st = o.optInt("startSec", o.optInt("start", 0))
            val en = o.optInt("endSec", o.optInt("end", st + 30))
            val tx = o.optString("text", "")
            if (tx.isNotBlank() && en > st) segs.add(Segment(st, en, tx))
        }
        if (segs.isEmpty()) throw IOException("AI returned no segments")
        val chapters = ArrayList<String>()
        val chArr = j.optJSONArray("chapters")
        if (chArr != null) {
            for (i in 0 until chArr.length()) {
                val o = chArr.getJSONObject(i)
                val t = o.optString("t", "")
                val ti = o.optString("title", "")
                if (t.isNotEmpty() && ti.isNotEmpty()) chapters.add(t + " — " + ti)
            }
        }
        val quiz = ArrayList<String>()
        val qArr = j.optJSONArray("quiz")
        if (qArr != null) {
            for (i in 0 until qArr.length()) {
                val o = qArr.getJSONObject(i)
                val q = o.optString("q", "")
                val a = o.optString("a", "")
                if (q.isNotEmpty() && a.isNotEmpty()) quiz.add("Q: " + q + "\nA: " + a)
            }
        }
        return RecapPlan(title, segs, chapters, quiz)
    }

    fun generateTts(apiKey: String, text: String, voice: String, speakingRate: Double = 1.0): Pair<ByteArray, Int> {
        if (speakingRate != 1.0) {
            try {
                return ttsCall(apiKey, text, voice, speakingRate)
            } catch (e: IOException) {
                val m = e.message ?: ""
                if (m.contains("400") || m.contains("INVALID")) {
                    return ttsCall(apiKey, text, voice, 1.0)
                }
                throw e
            }
        }
        return ttsCall(apiKey, text, voice, 1.0)
    }

    private fun ttsCall(apiKey: String, text: String, voice: String, rate: Double): Pair<ByteArray, Int> {
        val speech = JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))
        if (rate != 1.0) speech.put("speakingRate", rate)
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", speech))
        val resp = post(apiKey, "/v1beta/models/" + TTS_MODEL + ":generateContent", body.toString())
        val parts = resp.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        var data = ""
        var rateOut = 24000
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
            val mime = inline.optString("mimeType", inline.optString("mime_type", "audio/L16;rate=24000"))
            data = inline.optString("data", "")
            val m = Regex("rate=(\\d+)").find(mime)
            if (m != null) rateOut = m.groupValues[1].toInt()
        }
        if (data.isEmpty()) throw IOException("TTS returned no audio")
        return Pair(Base64.decode(data, Base64.DEFAULT), rateOut)
    }
}
