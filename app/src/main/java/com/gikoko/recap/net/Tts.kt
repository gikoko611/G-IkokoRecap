package com.gikoko.recap.net

import android.content.Context
import com.gikoko.recap.DebugLog
import com.gikoko.recap.Prefs

object Tts {

    fun defaultEdgeVoice(ctx: Context): String {
        return if (Prefs.lang(ctx) == "en") "en-US-AriaNeural" else Prefs.edgeVoice(ctx)
    }

    fun secondEdgeVoice(ctx: Context): String {
        if (Prefs.lang(ctx) == "en") return "en-US-GuyNeural"
        val main = Prefs.edgeVoice(ctx)
        return if (main.contains("Thiha")) EdgeTtsClient.VOICE_MY_FEMALE else EdgeTtsClient.VOICE_MY_MALE
    }

    fun generate(ctx: Context, apiKey: String, text: String, geminiVoice: String, speed: Double, edgeVoice: String = ""): Pair<ByteArray, Int> {
        if (Prefs.ttsEngine(ctx) == "edge") {
            val v = if (edgeVoice.isNotEmpty()) edgeVoice else defaultEdgeVoice(ctx)
            try {
                return EdgeTtsClient.synthesize(text, v, speed)
            } catch (e: Exception) {
                DebugLog.log(ctx, "edge tts failed, gemini fallback: " + (e.message ?: ""))
                if (apiKey.isEmpty()) throw e
            }
        }
        return GeminiClient.generateTts(apiKey, text, geminiVoice, speed)
    }
}
