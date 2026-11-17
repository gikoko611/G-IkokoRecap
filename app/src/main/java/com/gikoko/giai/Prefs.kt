package com.gikoko.giai

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) { sp = c.getSharedPreferences("giai", Context.MODE_PRIVATE) }

    var aiBase: String
        get() = sp.getString("ai_base", "https://openrouter.ai/api/v1") ?: ""
        set(v) = sp.edit().putString("ai_base", v).apply()
    var aiKey: String
        get() = sp.getString("ai_key", "") ?: ""
        set(v) = sp.edit().putString("ai_key", v).apply()
    var aiModel: String
        get() = sp.getString("ai_model", "openrouter/auto") ?: ""
        set(v) = sp.edit().putString("ai_model", v).apply()
    var aiFreeOnly: Boolean
        get() = sp.getBoolean("ai_free_only", true)
        set(v) = sp.edit().putBoolean("ai_free_only", v).apply()
    var ghToken: String
        get() = sp.getString("gh_token", "") ?: ""
        set(v) = sp.edit().putString("gh_token", v).apply()
    var ghRepo: String
        get() = sp.getString("gh_repo", "") ?: ""
        set(v) = sp.edit().putString("gh_repo", v).apply()
    var chatJson: String
        get() = sp.getString("chat_json", "[]") ?: "[]"
        set(v) = sp.edit().putString("chat_json", v).apply()
    var renderW: Int
        get() = sp.getInt("render_w", 480)
        set(v) = sp.edit().putInt("render_w", v).apply()
    var renderH: Int
        get() = sp.getInt("render_h", 854)
        set(v) = sp.edit().putInt("render_h", v).apply()
    var renderFps: Int
        get() = sp.getInt("render_fps", 24)
        set(v) = sp.edit().putInt("render_fps", v).apply()
    var renderFrames: Int
        get() = sp.getInt("render_frames", 72)
        set(v) = sp.edit().putInt("render_frames", v).apply()
}
