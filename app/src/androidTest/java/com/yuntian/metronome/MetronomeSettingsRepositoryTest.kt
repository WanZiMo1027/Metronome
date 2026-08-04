package com.yuntian.metronome

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuntian.metronome.metronome.MetronomeSettings
import com.yuntian.metronome.metronome.BeatPattern
import com.yuntian.metronome.metronome.CellSound
import com.yuntian.metronome.metronome.CustomPreset
import com.yuntian.metronome.metronome.PlaybackMode
import com.yuntian.metronome.metronome.SharedPreferencesMetronomeSettingsRepository
import com.yuntian.metronome.metronome.Subdivision
import com.yuntian.metronome.metronome.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetronomeSettingsRepositoryTest {
    @Test
    fun settingsSurviveRepositoryRecreationWhilePlaybackStateIsNotStored() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("metronome_settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val saved = MetronomeSettings(
            bpm = 186,
            timeSignature = TimeSignature.SIX_EIGHT,
            subdivision = Subdivision.SWING_LONG_SHORT,
            step = 5,
            accentEnabled = false,
            playbackMode = PlaybackMode.CUSTOM,
            customPattern = List(6) { index ->
                BeatPattern(
                    listOf(
                        if (index == 0) CellSound.ACCENT else CellSound.NORMAL,
                        CellSound.SILENT,
                        CellSound.NORMAL,
                    ),
                )
            },
        )

        SharedPreferencesMetronomeSettingsRepository(context).save(saved)

        assertEquals(saved, SharedPreferencesMetronomeSettingsRepository(context).load())
    }

    @Test
    fun customPresetsRoundTripAndCorruptDataFallsBackSafely() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences(
            "metronome_settings",
            android.content.Context.MODE_PRIVATE,
        )
        preferences.edit().clear().commit()
        val repository = SharedPreferencesMetronomeSettingsRepository(context)
        val presets = listOf(
            CustomPreset(
                id = "odd-meter",
                name = "奇数连音",
                bpm = 173,
                timeSignature = TimeSignature.THREE_FOUR,
                beats = listOf(
                    BeatPattern.normal(3),
                    BeatPattern.normal(5),
                    BeatPattern(listOf(CellSound.ACCENT, CellSound.SILENT)),
                ),
            ),
        )

        repository.savePresets(presets)
        assertEquals(presets, SharedPreferencesMetronomeSettingsRepository(context).loadPresets())

        preferences.edit().putString("custom_presets", "not-json").commit()
        assertEquals(emptyList<CustomPreset>(), repository.loadPresets())
    }

    @Test
    fun legacySettingsLoadInPresetModeWithDefaultCustomPattern() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences(
            "metronome_settings",
            android.content.Context.MODE_PRIVATE,
        )
        preferences.edit()
            .clear()
            .putInt("bpm", 92)
            .putString("time_signature", TimeSignature.THREE_FOUR.name)
            .putString("subdivision", Subdivision.EIGHTH.name)
            .putBoolean("accent", false)
            .commit()

        val restored = SharedPreferencesMetronomeSettingsRepository(context).load()

        assertEquals(92, restored.bpm)
        assertEquals(PlaybackMode.PRESET, restored.playbackMode)
        assertEquals(3, restored.customPattern.size)
        assertEquals(CellSound.ACCENT, restored.customPattern.first().cells.first())
    }
}
