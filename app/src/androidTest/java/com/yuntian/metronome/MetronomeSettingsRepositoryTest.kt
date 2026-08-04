package com.yuntian.metronome

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuntian.metronome.metronome.MetronomeSettings
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
        val saved = MetronomeSettings(
            bpm = 186,
            timeSignature = TimeSignature.SIX_EIGHT,
            subdivision = Subdivision.SWING_LONG_SHORT,
            step = 5,
            accentEnabled = false,
        )

        SharedPreferencesMetronomeSettingsRepository(context).save(saved)

        assertEquals(saved, SharedPreferencesMetronomeSettingsRepository(context).load())
    }
}
