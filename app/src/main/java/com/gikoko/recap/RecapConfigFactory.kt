package com.gikoko.recap

import android.content.Context
import com.gikoko.recap.recap.RecapConfig

object RecapConfigFactory {
    fun fromPrefs(context: Context, seriesContext: String = ""): RecapConfig {
        return RecapConfig(
            Prefs.apiKey(context),
            Prefs.provider(context),
            Prefs.baseUrl(context),
            Prefs.customKey(context),
            Prefs.customModel(context),
            Prefs.model(context),
            Prefs.voice(context),
            Prefs.minutes(context),
            Prefs.captions(context),
            Prefs.lang(context),
            Prefs.segFull(context),
            Prefs.watermark(context),
            Prefs.introOutro(context),
            Prefs.quality(context),
            Prefs.style(context),
            Prefs.speed(context),
            Prefs.customStyle(context),
            Prefs.vertical(context),
            Prefs.extBase(context),
            Prefs.extKey(context),
            Prefs.extModel(context),
            Prefs.transcriptMode(context),
            Prefs.transcriptFallback(context),
            Prefs.wmSize(context),
            Prefs.wmPos(context),
            Prefs.wmLogo(context),
            Prefs.theme(context),
            Prefs.planConfirm(context),
            Prefs.shorts(context),
            Prefs.podcast(context),
            Prefs.dialogue(context),
            Prefs.voice2(context),
            Prefs.bgmOn(context),
            Prefs.bgmPath(context),
            Prefs.bgmVol(context).toDoubleOrNull() ?: 0.2,
            seriesContext
        )
    }
}
