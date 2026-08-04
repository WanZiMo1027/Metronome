package com.yuntian.metronome.metronome

import android.content.Context
import androidx.core.content.edit

interface MetronomeSettingsRepository {
    fun load(): MetronomeSettings
    fun save(settings: MetronomeSettings)
}

class SharedPreferencesMetronomeSettingsRepository(context: Context) :
    MetronomeSettingsRepository {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): MetronomeSettings = MetronomeSettings(
        bpm = preferences.getInt(KEY_BPM, DEFAULT_BPM),
        timeSignature = TimeSignature.fromStored(preferences.getString(KEY_TIME_SIGNATURE, null)),
        subdivision = Subdivision.fromStored(preferences.getString(KEY_SUBDIVISION, null)),
        step = preferences.getInt(KEY_STEP, 1),
        accentEnabled = preferences.getBoolean(KEY_ACCENT, true),
    ).sanitized()

    override fun save(settings: MetronomeSettings) {
        val safeSettings = settings.sanitized()
        preferences.edit {
            putInt(KEY_BPM, safeSettings.bpm)
            putString(KEY_TIME_SIGNATURE, safeSettings.timeSignature.name)
            putString(KEY_SUBDIVISION, safeSettings.subdivision.name)
            putInt(KEY_STEP, safeSettings.step)
            putBoolean(KEY_ACCENT, safeSettings.accentEnabled)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "metronome_settings"
        const val KEY_BPM = "bpm"
        const val KEY_TIME_SIGNATURE = "time_signature"
        const val KEY_SUBDIVISION = "subdivision"
        const val KEY_STEP = "step"
        const val KEY_ACCENT = "accent"
    }
}
