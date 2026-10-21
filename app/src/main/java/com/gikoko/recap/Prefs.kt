package com.gikoko.recap

import android.content.Context

object Prefs {
    private const val NAME = "gikoko_one_recap"
    private const val KEY_API = "gemini_api_key"

    fun saveApiKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(KEY_API, key).apply()
    }

    fun apiKey(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_API, "") ?: ""
    }
}
