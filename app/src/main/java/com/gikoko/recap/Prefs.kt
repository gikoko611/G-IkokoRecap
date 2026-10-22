package com.gikoko.recap

import android.content.Context

object Prefs {
    private const val NAME = "gikoko_one_recap"

    const val KEY_API = "gemini_api_key"
    const val KEY_MODEL = "model"
    const val KEY_VOICE = "voice"
    const val KEY_MINUTES = "minutes"
    const val KEY_CAPTIONS = "captions"
    const val KEY_LANG = "lang"
    const val KEY_QUALITY = "quality"
    const val KEY_WATERMARK = "watermark"
    const val KEY_HAPTIC = "haptic"
    const val KEY_AUTOSAVE = "autosave"
    const val KEY_SEG_FULL = "seg_full"
    const val KEY_INTRO = "intro_outro"
    const val KEY_PROVIDER = "provider"
    const val KEY_BASE_URL = "base_url"
    const val KEY_CUSTOM_KEY = "custom_key"
    const val KEY_CUSTOM_MODEL = "custom_model"
    const val KEY_STYLE = "recap_style"
    const val KEY_SPEED = "recap_speed"
    const val KEY_VERTICAL = "recap_vertical"
    const val KEY_CUSTOM_STYLE = "custom_style"
    const val KEY_EXT_BASE = "ext_base"
    const val KEY_EXT_KEY = "ext_key"
    const val KEY_EXT_MODEL = "ext_model"
    const val KEY_TRANSCRIPT_MODE = "transcript_mode"
    const val KEY_TRANSCRIPT_FALLBACK = "transcript_fallback"

    private val validModels = setOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.8-flash-lite")
    private val validVoices = setOf("Sulafat", "Vindemiatrix", "Erinome", "Aoede", "Kore", "Leda", "Zephyr", "Despina", "Charon", "Iapetus", "Achird", "Orus", "Puck", "Fenrir", "Algenib", "Rasalgethi")
    private val validStyles = setOf("summary", "talk", "tech", "story", "highlights", "news", "funny", "eli5", "lesson", "trailer")

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun save(ctx: Context, key: String, value: String) {
        sp(ctx).edit().putString(key, value).apply()
    }

    fun str(ctx: Context, key: String, def: String): String {
        return sp(ctx).getString(key, def) ?: def
    }

    fun saveBool(ctx: Context, key: String, v: Boolean) {
        sp(ctx).edit().putBoolean(key, v).apply()
    }

    fun bool(ctx: Context, key: String, def: Boolean): Boolean {
        return sp(ctx).getBoolean(key, def)
    }

    fun saveInt(ctx: Context, key: String, v: Int) {
        sp(ctx).edit().putInt(key, v).apply()
    }

    fun int(ctx: Context, key: String, def: Int): Int {
        return sp(ctx).getInt(key, def)
    }

    fun saveApiKey(ctx: Context, key: String) = save(ctx, KEY_API, key)
    fun apiKey(ctx: Context): String = str(ctx, KEY_API, "")
    fun provider(ctx: Context): String = str(ctx, KEY_PROVIDER, "gemini")
    fun baseUrl(ctx: Context): String = str(ctx, KEY_BASE_URL, "https://openrouter.ai/api/v1")
    fun customKey(ctx: Context): String = str(ctx, KEY_CUSTOM_KEY, "")

    fun model(ctx: Context): String {
        val m = str(ctx, KEY_MODEL, "gemini-3.8-flash")
        if (m in validModels) return m
        save(ctx, KEY_MODEL, "gemini-3.8-flash")
        return "gemini-3.8-flash"
    }

    fun voice(ctx: Context): String {
        val v = str(ctx, KEY_VOICE, "Sulafat")
        val fixed = if (v == "Sulafet") "Sulafat" else v
        if (fixed in validVoices) {
            if (fixed != v) save(ctx, KEY_VOICE, fixed)
            return fixed
        }
        save(ctx, KEY_VOICE, "Sulafat")
        return "Sulafat"
    }

    fun style(ctx: Context): String {
        val s = str(ctx, KEY_STYLE, "summary")
        if (s in validStyles) return s
        save(ctx, KEY_STYLE, "summary")
        return "summary"
    }

    fun customModel(ctx: Context): String = str(ctx, KEY_CUSTOM_MODEL, "")
    fun minutes(ctx: Context): Int = int(ctx, KEY_MINUTES, 0)

    fun speed(ctx: Context): Double {
        val s = str(ctx, KEY_SPEED, "1.0")
        return when (s) {
            "0.85" -> 0.85
            "1.2" -> 1.2
            else -> 1.0
        }
    }

    fun customStyle(ctx: Context): String = str(ctx, KEY_CUSTOM_STYLE, "")

    fun extBase(ctx: Context): String = str(ctx, KEY_EXT_BASE, "https://openrouter.ai/api/v1")
    fun extKey(ctx: Context): String = str(ctx, KEY_EXT_KEY, "")
    fun extModel(ctx: Context): String = str(ctx, KEY_EXT_MODEL, "")
    fun transcriptMode(ctx: Context): Boolean = bool(ctx, KEY_TRANSCRIPT_MODE, false)
    fun transcriptFallback(ctx: Context): Boolean = bool(ctx, KEY_TRANSCRIPT_FALLBACK, true)

    fun vertical(ctx: Context): Boolean = str(ctx, KEY_VERTICAL, "0") == "1"
    fun captions(ctx: Context): Boolean = bool(ctx, KEY_CAPTIONS, true)
    fun lang(ctx: Context): String = str(ctx, KEY_LANG, "my")
    fun quality(ctx: Context): Int = int(ctx, KEY_QUALITY, 1)
    fun watermark(ctx: Context): Boolean = bool(ctx, KEY_WATERMARK, true)
    fun haptic(ctx: Context): Boolean = bool(ctx, KEY_HAPTIC, true)
    fun autosave(ctx: Context): Boolean = bool(ctx, KEY_AUTOSAVE, false)
    fun segFull(ctx: Context): Boolean = bool(ctx, KEY_SEG_FULL, false)
    fun introOutro(ctx: Context): Boolean = bool(ctx, KEY_INTRO, true)
}
