package com.yuntian.metronome.metronome

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountInAudioTimelineTest {
    @Test
    fun `count in and playback keep one continuous fractional frame phase`() {
        val pattern = listOf(
            BeatPattern.normal(),
            BeatPattern.normal(2),
            BeatPattern.normal(3),
        )
        val settings = MetronomeSettings(
            bpm = 137,
            timeSignature = TimeSignature.THREE_FOUR,
            playbackMode = PlaybackMode.CUSTOM,
            customPattern = pattern,
            countInEnabled = true,
        )
        val sequencer = PulseSequencer(settings)
        val timeline = AudioFrameTimeline()

        repeat(12) {
            val event = sequencer.next(settings, 0L)
            timeline.advance(event.bpm, event.stepWeights, event.subdivisionIndex)
        }

        val exactFrames = 6.0 * METRONOME_PCM_SAMPLE_RATE * 60.0 / 137.0
        assertTrue(abs(timeline.currentFrame - exactFrames) < 1.0)
    }

    @Test
    fun `number cue starts on the exact first beat frame and mixes with its click`() {
        val firstMeter = ArrangementMeter(1, 4)
        val changedMeter = ArrangementMeter(2, 4)
        val numberSamples = List(11) { index ->
            if (index == 1) shortArrayOf(1_000, 1_000) else shortArrayOf(0, 0)
        }
        val renderer = PcmTimelineRenderer(
            clickSamples = ClickSamples(
                downbeat = shortArrayOf(100, 100),
                otherBeat = shortArrayOf(50, 50),
                numbers = numberSamples,
            ),
            arrangement = listOf(
                ArrangementChange(1, 300, firstMeter, defaultArrangementPattern(firstMeter)),
                ArrangementChange(2, 300, changedMeter, defaultArrangementPattern(changedMeter)),
            ),
            arrangementStartMeasure = 1,
            countInEnabled = false,
            settingsProvider = { MetronomeSettings() },
            tempoProvider = { 300 },
            startFrame = 0L,
        )
        val scheduled = mutableListOf<ScheduledPulse>()
        val secondMeasureFrame = METRONOME_PCM_SAMPLE_RATE * 60 / 300

        val output = renderer.render(secondMeasureFrame + 1, scheduled::add)

        assertEquals(listOf(NumberCue(2)), scheduled[1].event.numberCues)
        assertEquals(1_100, output[secondMeasureFrame * 2].toInt())
        assertEquals(1_100, output[secondMeasureFrame * 2 + 1].toInt())
        assertEquals(
            secondMeasureFrame.toLong(),
            numberCueStartFrame(secondMeasureFrame.toLong(), 0L),
        )
        assertEquals(
            secondMeasureFrame + 13_230L,
            numberCueStartFrame(secondMeasureFrame.toLong(), DENOMINATOR_CUE_DELAY_MILLIS),
        )
    }
}
