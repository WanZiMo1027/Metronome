package com.yuntian.metronome

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuntian.metronome.metronome.AndroidMetronomeEngine
import com.yuntian.metronome.metronome.MetronomeSettings
import com.yuntian.metronome.metronome.Subdivision
import com.yuntian.metronome.metronome.TimeSignature
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountInAndroidMetronomeEngineTest {
    @Test
    fun countInEventsPrecedeTheFirstPlaybackMeasureOnTheSameAudioTrack() {
        val engine = AndroidMetronomeEngine(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val pulses = CountDownLatch(3)
        val events = CopyOnWriteArrayList<Triple<Boolean, Int, Int>>()
        val failure = AtomicReference<Throwable?>(null)

        try {
            assertTrue(
                engine.start(
                    settings = MetronomeSettings(
                        bpm = 300,
                        timeSignature = TimeSignature.TWO_FOUR,
                        subdivision = Subdivision.QUARTER,
                        countInEnabled = true,
                    ),
                    onPulse = { event ->
                        events += Triple(event.isCountIn, event.beat, event.subdivisionIndex)
                        pulses.countDown()
                    },
                    onError = { failure.set(it) },
                ),
            )

            assertTrue(pulses.await(2, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertEquals(
                listOf(
                    Triple(true, 1, 0),
                    Triple(true, 2, 0),
                    Triple(false, 1, 0),
                ),
                events.take(3),
            )
        } finally {
            engine.release()
        }
    }
}
