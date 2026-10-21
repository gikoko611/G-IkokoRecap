package com.gikoko.recap.recap

object WavUtil {

    fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val totalLen = 36 + pcm.size
        val out = ByteArray(44 + pcm.size)
        out[0] = 'R'.code.toByte()
        out[1] = 'I'.code.toByte()
        out[2] = 'F'.code.toByte()
        out[3] = 'F'.code.toByte()
        writeIntLE(out, 4, totalLen)
        out[8] = 'W'.code.toByte()
        out[9] = 'A'.code.toByte()
        out[10] = 'V'.code.toByte()
        out[11] = 'E'.code.toByte()
        out[12] = 'f'.code.toByte()
        out[13] = 'm'.code.toByte()
        out[14] = 't'.code.toByte()
        out[15] = ' '.code.toByte()
        writeIntLE(out, 16, 16)
        writeShortLE(out, 20, 1)
        writeShortLE(out, 22, channels)
        writeIntLE(out, 24, sampleRate)
        writeIntLE(out, 28, byteRate)
        writeShortLE(out, 32, blockAlign)
        writeShortLE(out, 34, bits)
        out[36] = 'd'.code.toByte()
        out[37] = 'a'.code.toByte()
        out[38] = 't'.code.toByte()
        out[39] = 'a'.code.toByte()
        writeIntLE(out, 40, pcm.size)
        System.arraycopy(pcm, 0, out, 44, pcm.size)
        return out
    }

    fun writeIntLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v shr 8) and 0xff).toByte()
        b[off + 2] = ((v shr 16) and 0xff).toByte()
        b[off + 3] = ((v shr 24) and 0xff).toByte()
    }

    fun writeShortLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v shr 8) and 0xff).toByte()
    }

    fun readIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)
    }

    fun wavDurationMs(wav: ByteArray): Long {
        if (wav.size <= 44) return 0
        val rate = readIntLE(wav, 24)
        val dataSize = readIntLE(wav, 40)
        if (rate <= 0) return 0
        return dataSize * 1000L / (rate * 2L)
    }
}
