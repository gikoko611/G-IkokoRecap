package com.gikoko.recap.net

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object EdgeTtsClient {

    private const val TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val BASE_WS = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    const val VOICE_MY_FEMALE = "my-MM-NilarNeural"
    const val VOICE_MY_MALE = "my-MM-ThihaNeural"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun synthesize(text: String, voice: String, speed: Double): Pair<ByteArray, Int> {
        val t = text.trim()
        if (t.isEmpty()) throw IOException("Edge TTS: စာသား ဗလာဖြစ်နေပါတယ်")
        if (t.length <= 1800) return synthesizeOnce(t, voice, speed)
        val parts = splitText(t, 1800)
        val pcmAll = ArrayList<ByteArray>()
        var rate = 24000
        for (p in parts) {
            val one = synthesizeOnce(p, voice, speed)
            if (pcmAll.isEmpty()) rate = one.second
            pcmAll.add(one.first)
        }
        var total = 0
        for (b in pcmAll) total += b.size
        val out = ByteArray(total)
        var off = 0
        for (b in pcmAll) {
            System.arraycopy(b, 0, out, off, b.size)
            off += b.size
        }
        return Pair(out, rate)
    }

    private fun splitText(text: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var lastBreak = -1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '။' || c == '.' || c == '!' || c == '?' || c == '\n') lastBreak = i + 1
            if (i - start >= max) {
                val cut = if (lastBreak > start) lastBreak else start + max
                val piece = text.substring(start, cut).trim()
                if (piece.isNotEmpty()) out.add(piece)
                start = cut
                lastBreak = -1
            }
            i++
        }
        if (start < text.length) {
            val piece = text.substring(start).trim()
            if (piece.isNotEmpty()) out.add(piece)
        }
        return out
    }

    private fun synthesizeOnce(text: String, voice: String, speed: Double): Pair<ByteArray, Int> {
        val connectionId = hexId()
        val url = BASE_WS + "?TrustedClientToken=" + TRUSTED_TOKEN +
            "&Sec-MS-GEC=" + secMsGec() +
            "&Sec-MS-GEC-Version=1-130.0.2849.68" +
            "&ConnectionId=" + connectionId
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .build()

        val audio = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        val failureHolder = arrayOfNulls<Exception>(1)

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val date = dateHeader()
                val config = "X-Timestamp:" + date + "\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                webSocket.send(config)
                val ratePct = Math.round((speed - 1.0) * 100.0).toInt()
                val rateStr = (if (ratePct >= 0) "+" else "") + ratePct + "%"
                val ssml = "X-RequestId:" + connectionId + "\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:" + date + "Z\r\nPath:ssml\r\n\r\n" +
                    "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><voice name='" + voice + "'><prosody pitch='+0Hz' rate='" + rateStr + "' volume='+0%'>" + escapeXml(text) + "</prosody></voice></speak>"
                webSocket.send(ssml)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val arr = bytes.toByteArray()
                if (arr.size < 2) return
                val headerLen = ((arr[0].toInt() and 0xff) shl 8) or (arr[1].toInt() and 0xff)
                if (headerLen <= 0 || arr.size < headerLen + 2) return
                val header = String(arr, 2, headerLen, Charsets.UTF_8)
                if (header.contains("Path:audio")) {
                    audio.write(arr, headerLen + 2, arr.size - headerLen - 2)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) done.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failureHolder[0] = IOException("Edge TTS: " + (t.message ?: "connection failed"))
                done.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.countDown()
            }
        })

        try {
            if (!done.await(90, TimeUnit.SECONDS)) {
                throw IOException("Edge TTS: timeout (90s)")
            }
        } finally {
            runCatching { ws.cancel() }
        }
        val failure = failureHolder[0]
        if (failure != null) throw failure
        val mp3 = audio.toByteArray()
        if (mp3.size < 800) throw IOException("Edge TTS: audio မရရှိပါ (403 token)")
        return mp3ToPcm(mp3)
    }

    private fun secMsGec(): String {
        val unix = System.currentTimeMillis() / 1000L
        var ticks = (unix + 11644473600L) * 10000000L
        ticks = (ticks + 1500000L) / 3000000L * 3000000L
        val s = ticks.toString() + "3084300" + TRUSTED_TOKEN
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.US_ASCII))
        val sb = StringBuilder()
        for (b in digest) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString().uppercase(Locale.US)
    }

    private fun dateHeader(): String {
        val fmt = SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US)
        return fmt.format(Date()) + " GMT+0000 (Coordinated Universal Time)"
    }

    private fun hexId(): String {
        val c = "0123456789abcdef"
        val sb = StringBuilder()
        for (i in 0 until 32) sb.append(c[(Math.random() * 16).toInt()])
        return sb.toString()
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
    }

    private fun mp3ToPcm(mp3: ByteArray): Pair<ByteArray, Int> {
        val tmp = File.createTempFile("edge_tts_", ".mp3")
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            tmp.writeBytes(mp3)
            ex.setDataSource(tmp.absolutePath)
            var trackIdx = -1
            var trackFormat: MediaFormat? = null
            var mime = ""
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    trackIdx = i
                    trackFormat = f
                    mime = m
                    break
                }
            }
            if (trackIdx < 0 || trackFormat == null) throw IOException("Edge TTS: MP3 audio track မတွေ့ပါ")
            ex.selectTrack(trackIdx)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInput = false
            var sawOutput = false
            var outRate = 24000
            var outChannels = 1
            var guard = 0
            while (!sawOutput && guard < 20000) {
                guard++
                if (!sawInput) {
                    val inIdx = codec.dequeueInputBuffer(10000L)
                    if (inIdx >= 0) {
                        val ib = codec.getInputBuffer(inIdx)
                        if (ib == null) {
                            sawInput = true
                        } else {
                            val sz = ex.readSampleData(ib, 0)
                            if (sz < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInput = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sz, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10000L)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(outIdx)
                        if (ob != null) {
                            val chunk = ByteArray(info.size)
                            ob.get(chunk)
                            if (outChannels == 2) {
                                val mono = ByteArray(chunk.size / 4 * 2)
                                var j = 0
                                var k = 0
                                while (j + 3 < chunk.size) {
                                    val l = (chunk[j].toInt() and 0xff) or (chunk[j + 1].toInt() shl 8)
                                    val r = (chunk[j + 2].toInt() and 0xff) or (chunk[j + 3].toInt() shl 8)
                                    val avg = (l + r) / 2
                                    mono[k] = (avg and 0xff).toByte()
                                    mono[k + 1] = ((avg shr 8) and 0xff).toByte()
                                    k += 2
                                    j += 4
                                }
                                pcm.write(mono, 0, k)
                            } else {
                                pcm.write(chunk)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutput = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    outRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    outChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            if (pcm.size() == 0) throw IOException("Edge TTS: MP3 decode ဗလာ")
            return Pair(pcm.toByteArray(), outRate)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
            runCatching { tmp.delete() }
        }
    }
}
