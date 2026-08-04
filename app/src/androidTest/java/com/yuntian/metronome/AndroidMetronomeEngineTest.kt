package com.yuntian.metronome

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuntian.metronome.metronome.AndroidMetronomeEngine
import com.yuntian.metronome.metronome.MetronomeSettings
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AndroidMetronomeEngineTest {
    @Test
    fun audioEngineStartsEmitsBeatsAndStops() {
        val engine = AndroidMetronomeEngine()
        val beats = CountDownLatch(2)
        val failure = AtomicReference<Throwable?>(null)

        try {
            val started = engine.start(
                settings = MetronomeSettings(bpm = 300),
                onPulse = { beats.countDown() },
                onError = { failure.set(it) },
            )

            assertTrue("Audio engine should initialize", started)
            assertTrue("Two beat callbacks should arrive", beats.await(2, TimeUnit.SECONDS))
            assertNull(failure.get())
        } finally {
            engine.stop()
        }
    }
}
