package com.gikoko.recap

import android.content.Context
import android.graphics.Color

object ThemePalette {
    fun accent(context: Context): Int = when (Prefs.uiTheme(context)) {
        "blue" -> Color.parseColor("#35D9FF")
        "violet" -> Color.parseColor("#C77DFF")
        else -> Color.parseColor("#39FF14")
    }

    fun highlight(context: Context): Int = when (Prefs.uiTheme(context)) {
        "blue" -> Color.parseColor("#A7F0FF")
        "violet" -> Color.parseColor("#E3C2FF")
        else -> Color.parseColor("#B6FF00")
    }
}
