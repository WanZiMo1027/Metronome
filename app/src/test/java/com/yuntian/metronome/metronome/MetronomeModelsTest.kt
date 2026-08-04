package com.yuntian.metronome.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeModelsTest {
    @Test
    fun `tempo intervals are exact at supported reference values`() {
        assertEquals(2_000_000_000L, MetronomeTiming.intervalNanos(30))
        assertEquals(500_000_000L, MetronomeTiming.intervalNanos(120))
        assertEquals(200_000_000L, MetronomeTiming.intervalNanos(300))
    }

    @Test
    fun `absolute deadlines avoid drift and late work never catches up in a burst`() {
        val interval = MetronomeTiming.intervalNanos(120)
        val firstDeadline = MetronomeTiming.nextDeadlineNanos(1_000_000_000L, 120)
        assertEquals(1_000_000_000L + interval, firstDeadline)

        val slightlyLate = firstDeadline + 10_000_000L
        assertEquals(
            firstDeadline,
            MetronomeTiming.resolveScheduledAtNanos(1_000_000_000L, slightlyLate, 120),
        )

        val severelyLate = firstDeadline + interval + 1L
        assertEquals(
            severelyLate,
            MetronomeTiming.resolveScheduledAtNanos(1_000_000_000L, severelyLate, 120),
        )
        assertEquals(
            severelyLate + interval,
            MetronomeTiming.nextDeadlineNanos(severelyLate, 120),
        )
    }

    @Test
    fun `every subdivision exactly fills one beat`() {
        val beatDuration = MetronomeTiming.intervalNanos(120)

        Subdivision.entries.forEach { subdivision ->
            val durations = subdivision.stepWeights.indices.map { index ->
                MetronomeTiming.stepDurationNanos(120, subdivision, index)
            }
            assertEquals(subdivision.accessibilityLabel, beatDuration, durations.sum())
        }

        assertEquals(
            listOf(375_000_000L, 125_000_000L),
            Subdivision.SWING_LONG_SHORT.stepWeights.indices.map { index ->
                MetronomeTiming.stepDurationNanos(120, Subdivision.SWING_LONG_SHORT, index)
            },
        )
        assertEquals(
            listOf(125_000_000L, 375_000_000L),
            Subdivision.SWING_SHORT_LONG.stepWeights.indices.map { index ->
                MetronomeTiming.stepDurationNanos(120, Subdivision.SWING_SHORT_LONG, index)
            },
        )
    }

    @Test
    fun `triplet rounding resets cleanly at every beat`() {
        val durations = Subdivision.EIGHTH_TRIPLET.stepWeights.indices.map { index ->
            MetronomeTiming.stepDurationNanos(120, Subdivision.EIGHTH_TRIPLET, index)
        }

        assertEquals(listOf(166_666_666L, 166_666_667L, 166_666_667L), durations)
        assertEquals(MetronomeTiming.intervalNanos(120), durations.sum())
    }

    @Test
    fun `all subdivisions emit their pulses from top to bottom before advancing the beat`() {
        Subdivision.entries.forEach { subdivision ->
            val sequencer = PulseSequencer(TimeSignature.TWO_FOUR, subdivision)
            val firstBeat = List(subdivision.stepCount) { sequencer.nextPulse() }

            assertEquals(
                subdivision.accessibilityLabel,
                subdivision.stepWeights.indices.toList(),
                firstBeat.map { it.subdivisionIndex },
            )
            assertTrue(firstBeat.all { it.beat == 1 && it.subdivision == subdivision })

            val secondBeat = sequencer.nextPulse()
            assertEquals(2, secondBeat.beat)
            assertEquals(0, secondBeat.subdivisionIndex)
        }
    }

    @Test
    fun `settings sanitize bpm and step boundaries`() {
        assertEquals(
            MetronomeSettings(bpm = 30, step = 1),
            MetronomeSettings(bpm = -4, step = 2).sanitized(),
        )
        assertEquals(
            MetronomeSettings(bpm = 300, step = 5),
            MetronomeSettings(bpm = 999, step = 5).sanitized(),
        )
    }

    @Test
    fun `manual bpm input accepts only integers in range`() {
        assertEquals(30, parseBpmInput("30"))
        assertEquals(120, parseBpmInput(" 120 "))
        assertEquals(300, parseBpmInput("300"))
        assertNull(parseBpmInput(""))
        assertNull(parseBpmInput("29"))
        assertNull(parseBpmInput("301"))
        assertNull(parseBpmInput("120.5"))
    }

    @Test
    fun `time signature changes only after current measure completes`() {
        val sequencer = PulseSequencer(TimeSignature.FOUR_FOUR)

        assertEquals(1, sequencer.nextPulse(TimeSignature.FOUR_FOUR).beat)
        assertEquals(2, sequencer.nextPulse(TimeSignature.THREE_FOUR).beat)
        assertEquals(3, sequencer.nextPulse(TimeSignature.THREE_FOUR).beat)
        assertEquals(4, sequencer.nextPulse(TimeSignature.THREE_FOUR).beat)

        val firstBeatOfNewMeasure = sequencer.nextPulse(TimeSignature.THREE_FOUR)
        assertEquals(1, firstBeatOfNewMeasure.beat)
        assertEquals(TimeSignature.THREE_FOUR, firstBeatOfNewMeasure.timeSignature)
        assertEquals(AccentLevel.DOWNBEAT, firstBeatOfNewMeasure.accentLevel)
    }

    @Test
    fun `six eight emits six eighth note positions`() {
        val sequencer = PulseSequencer(TimeSignature.SIX_EIGHT)
        val beats = List(8) {
            sequencer.nextPulse(TimeSignature.SIX_EIGHT)
        }

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 1, 2), beats.map { it.beat })
        assertEquals(
            listOf(
                AccentLevel.DOWNBEAT,
                AccentLevel.BEAT,
                AccentLevel.BEAT,
                AccentLevel.BEAT,
                AccentLevel.BEAT,
                AccentLevel.BEAT,
                AccentLevel.DOWNBEAT,
                AccentLevel.BEAT,
            ),
            beats.map { it.accentLevel },
        )
    }

    @Test
    fun `accent levels distinguish downbeat beat and subdivision`() {
        val sequencer = PulseSequencer(TimeSignature.TWO_FOUR, Subdivision.EIGHTH)

        assertEquals(AccentLevel.DOWNBEAT, sequencer.nextPulse().accentLevel)
        assertEquals(AccentLevel.SUBDIVISION, sequencer.nextPulse().accentLevel)
        assertEquals(AccentLevel.BEAT, sequencer.nextPulse().accentLevel)

        val accentOff = PulseSequencer(TimeSignature.TWO_FOUR, Subdivision.EIGHTH)
        assertEquals(AccentLevel.BEAT, accentOff.nextPulse(accentEnabled = false).accentLevel)
        assertEquals(AccentLevel.SUBDIVISION, accentOff.nextPulse(accentEnabled = false).accentLevel)
    }

    @Test
    fun `signature and subdivision change together at the next measure`() {
        val sequencer = PulseSequencer(TimeSignature.TWO_FOUR, Subdivision.EIGHTH)
        val oldMeasure = List(4) {
            sequencer.nextPulse(
                requestedSignature = TimeSignature.THREE_FOUR,
                requestedSubdivision = Subdivision.EIGHTH_TRIPLET,
            )
        }

        assertEquals(listOf(1, 1, 2, 2), oldMeasure.map { it.beat })
        assertTrue(oldMeasure.all { it.subdivision == Subdivision.EIGHTH })

        val newMeasure = sequencer.nextPulse(
            requestedSignature = TimeSignature.THREE_FOUR,
            requestedSubdivision = Subdivision.EIGHTH_TRIPLET,
        )
        assertEquals(1, newMeasure.beat)
        assertEquals(0, newMeasure.subdivisionIndex)
        assertEquals(TimeSignature.THREE_FOUR, newMeasure.timeSignature)
        assertEquals(Subdivision.EIGHTH_TRIPLET, newMeasure.subdivision)
    }

    @Test
    fun `requesting the active subdivision again cancels a pending change`() {
        val sequencer = PulseSequencer(TimeSignature.TWO_FOUR, Subdivision.EIGHTH)

        sequencer.nextPulse(requestedSubdivision = Subdivision.SIXTEENTH)
        repeat(3) {
            sequencer.nextPulse(requestedSubdivision = Subdivision.EIGHTH)
        }
        val nextMeasure = sequencer.nextPulse(requestedSubdivision = Subdivision.EIGHTH)

        assertEquals(Subdivision.EIGHTH, nextMeasure.subdivision)
        assertEquals(0, nextMeasure.subdivisionIndex)
    }

    @Test
    fun `unknown stored signature restores four four default`() {
        assertEquals(TimeSignature.FOUR_FOUR, TimeSignature.fromStored("UNKNOWN"))
        assertEquals(TimeSignature.SIX_EIGHT, TimeSignature.fromStored("SIX_EIGHT"))
        assertEquals(Subdivision.QUARTER, Subdivision.fromStored("UNKNOWN"))
        assertEquals(Subdivision.SIXTEENTH, Subdivision.fromStored("SIXTEENTH"))
    }

    private fun PulseSequencer.nextPulse(
        requestedSignature: TimeSignature = activeTimeSignature,
        requestedSubdivision: Subdivision = activeSubdivision,
        accentEnabled: Boolean = true,
    ): PulseEvent = next(
        requestedSignature = requestedSignature,
        requestedSubdivision = requestedSubdivision,
        accentEnabled = accentEnabled,
        scheduledAtNanos = 0L,
    )
}
