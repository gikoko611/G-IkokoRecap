package com.gikoko.recap.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object TranscriptClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun extractVideoId(url: String): String? {
        val u = url.trim()
        val m1 = Regex("[?&]v=([a-zA-Z0-9_-]{6,})").find(u)
        if (m1 != null) return m1.groupValues[1]
        val m2 = Regex("youtu\\.be/([a-zA-Z0-9_-]{6,})").find(u)
        if (m2 != null) return m2.groupValues[1]
        val m3 = Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{6,})").find(u)
        if (m3 != null) return m3.groupValues[1]
        val m4 = Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{6,})").find(u)
        if (m4 != null) return m4.groupValues[1]
        return null
    }

    private fun httpGet(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(200))
            return txt
        }
    }

    fun fetchTranscript(videoId: String, onLog: (String) -> Unit): String {
        val html = httpGet("https://www.youtube.com/watch?v=" + videoId + "&hl=en")
        onLog("Watch page " + html.length + " chars")
        val tracksMatch = Regex("\"captionTracks\":\\[(.*?)\\]").find(html)
        if (tracksMatch == null) throw IOException("ဒီ video မှာ caption/transcript မတွေ့ပါ - YouTube မှာ caption ပိတ်ထားနိုင်ပါတယ်")
        val tracksRaw = tracksMatch.groupValues[1]
        val baseUrlR = Regex("\"baseUrl\":\\s*\"(.*?)\"")
        val langR = Regex("\"languageCode\":\\s*\"(.*?)\"")
        val kindR = Regex("\"kind\":\\s*\"(.*?)\"")
        val entries = ArrayList<Triple<String, String, String>>()
        var from = 0
        while (true) {
            val m = baseUrlR.find(tracksRaw, from)
            if (m == null) break
            val u = m.groupValues[1].replace("\\u0026", "&").replace("\\/", "/")
            val end = minOf(tracksRaw.length, m.range.last + 1 + 700)
            val after = if (m.range.last + 1 < end) tracksRaw.substring(m.range.last + 1, end) else ""
            val lang = langR.find(after)?.groupValues?.get(1) ?: ""
            val kind = kindR.find(after)?.groupValues?.get(1) ?: ""
            entries.add(Triple(u, lang, kind))
            from = m.range.last + 1
        }
        if (entries.isEmpty()) throw IOException("caption track မတွေ့ပါ")
        var chosen = entries[0]
        val my = entries.firstOrNull { it.second == "my" }
        if (my != null) {
            chosen = my
        } else {
            val manualEn = entries.firstOrNull { it.second == "en" && it.third != "asr" }
            if (manualEn != null) chosen = manualEn
        }
        onLog("Caption track lang=" + chosen.second + " kind=" + chosen.third)
        val data = httpGet(chosen.first + "&fmt=json3")
        val events = JSONObject(data).optJSONArray("events") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until events.length()) {
            val ev = events.getJSONObject(i)
            val segs = ev.optJSONArray("segs") ?: continue
            val startMs = ev.optLong("tStartMs", 0L)
            val mm = startMs / 60000L
            val ss = (startMs % 60000L) / 1000L
            val buf = StringBuilder()
            for (j in 0 until segs.length()) {
                val s = segs.getJSONObject(j).optString("utf8", "")
                buf.append(s.replace('\n', ' '))
            }
            val line = buf.toString().trim()
            if (line.isNotEmpty()) {
                sb.append('[').append(mm).append(':').append(String.format("%02d", ss)).append("] ").append(line).append('\n')
            }
        }
        val out = sb.toString()
        if (out.isBlank()) throw IOException("Transcript ဗလာဖြစ်နေပါတယ်")
        if (out.length > 90000) return out.substring(0, 90000)
        return out
    }
}
