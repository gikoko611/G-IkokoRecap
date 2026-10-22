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
    fun model(ctx: Context): String = str(ctx, KEY_MODEL, "gemini-2.5-flash")
    fun voice(ctx: Context): String = str(ctx, KEY_VOICE, "Puck")
    fun minutes(ctx: Context): Int = int(ctx, KEY_MINUTES, 0)
    fun captions(ctx: Context): Boolean = bool(ctx, KEY_CAPTIONS, true)
    fun lang(ctx: Context): String = str(ctx, KEY_LANG, "my")
    fun quality(ctx: Context): Int = int(ctx, KEY_QUALITY, 1)
    fun watermark(ctx: Context): Boolean = bool(ctx, KEY_WATERMARK, true)
    fun haptic(ctx: Context): Boolean = bool(ctx, KEY_HAPTIC, true)
    fun autosave(ctx: Context): Boolean = bool(ctx, KEY_AUTOSAVE, false)
    fun segFull(ctx: Context): Boolean = bool(ctx, KEY_SEG_FULL, false)
    fun introOutro(ctx: Context): Boolean = bool(ctx, KEY_INTRO, true)
}
