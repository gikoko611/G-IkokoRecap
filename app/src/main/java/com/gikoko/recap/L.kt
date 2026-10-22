package com.gikoko.recap

import android.content.Context

object L {
    fun en(ctx: Context): Boolean = Prefs.uiLang(ctx) == "en"
    fun s(ctx: Context, mm: String, eng: String): String = if (en(ctx)) eng else mm
}