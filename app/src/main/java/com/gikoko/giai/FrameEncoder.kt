package com.gikoko.giai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** H.264 encoder: PNG frame sequence -> mp4 (ffmpeg-style pipeline, fully on-device). */
object FrameEncoder {

    fun encodePngs(pngs: List<File>, fps: Int, out: File): Boolean {
        if (pngs.isEmpty()) return false
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val first = BitmapFactory.decodeFile(pngs[0].absolutePath, opts) ?: return false
        val w = first.width - (first.width % 2)
        val h = first.height - (first.height % 2)
        first.recycle()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        format.setInteger(MediaFormat.KEY_BIT_RATE, (w * h * fps / 8).coerceIn(1_000_000, 12_000_000))
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)

        val codec = MediaCodec.createEncoderByType("video/avc")
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxStarted = false
        val info = MediaCodec.BufferInfo()
        val frameUs = 1_000_000L / fps.coerceAtLeast(1)

        fun drain(end: Boolean) {
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return else continue }
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start(); muxStarted = true; continue
                }
                if (idx < 0) continue
                val buf = codec.getOutputBuffer(idx) ?: continue
                if (info.size > 0 && muxStarted) {
                    info.presentationTimeUs = lastPts
                    muxer.writeSampleData(track, buf, info)
                }
                codec.releaseOutputBuffer(idx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }

        var lastPts = 0L
        try {
            for ((i, f) in pngs.withIndex()) {
                var inIdx = -1
                while (inIdx < 0) {
                    inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx < 0) drain(false)
                }
                val img = codec.getInputImage(inIdx) ?: continue
                img.cropRect = Rect(0, 0, w, h)
                val bmp = BitmapFactory.decodeFile(f.absolutePath, opts) ?: continue
                fillYuv(img, bmp, w, h)
                bmp.recycle()
                val pts = i * frameUs
                lastPts = pts
                codec.queueInputBuffer(inIdx, 0, w * h * 3 / 2, pts, 0)
                drain(false)
            }
            var eos = -1
            while (eos < 0) { eos = codec.dequeueInputBuffer(10_000); if (eos < 0) drain(false) }
            codec.queueInputBuffer(eos, 0, 0, pngs.size * frameUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
        }
        return out.length() > 0
    }

    private fun clamp(v: Int) = v.coerceIn(0, 255)

    private fun fillYuv(img: android.media.Image, bmp: Bitmap, w: Int, h: Int) {
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val yP = img.planes[0]
        val uP = img.planes[1]
        val vP = img.planes[2]
        val yBuf: ByteBuffer = yP.buffer
        val uvBuf: ByteBuffer = uP.buffer
        val vBuf: ByteBuffer = vP.buffer
        val yRow = ByteArray(w)
        for (r in 0 until h) {
            var k = 0
            for (c in 0 until w) {
                val p = px[r * w + c]
                val R = (p shr 16) and 0xFF
                val G = (p shr 8) and 0xFF
                val B = p and 0xFF
                yRow[k++] = clamp((77 * R + 150 * G + 29 * B) shr 8).toByte()
            }
            val off = r * yP.rowStride
            for (c in 0 until w) yBuf.put(off + c, yRow[c])
        }
        val semi = uP.pixelStride == 2
        for (r in 0 until h / 2) {
            for (c in 0 until w / 2) {
                val p = px[(2 * r) * w + 2 * c]
                val R = (p shr 16) and 0xFF
                val G = (p shr 8) and 0xFF
                val B = p and 0xFF
                val u = clamp(((B - Y(R, G, B)) * 564 shr 10) + 128)
                val v = clamp(((R - Y(R, G, B)) * 713 shr 10) + 128)
                if (semi) {
                    val off = r * uP.rowStride + c * 2
                    uvBuf.put(off, u.toByte())
                    uvBuf.put(off + 1, v.toByte())
                } else {
                    vBuf.put(r * vP.rowStride + c * vP.pixelStride, v.toByte())
                    uvBuf.put(r * uP.rowStride + c * uP.pixelStride, u.toByte())
                }
            }
        }
    }

    private fun Y(R: Int, G: Int, B: Int) = (77 * R + 150 * G + 29 * B) shr 8
}
