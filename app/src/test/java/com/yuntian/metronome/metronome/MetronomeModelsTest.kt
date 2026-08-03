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
        val sequencer = BeatSequencer(TimeSignature.FOUR_FOUR)

        assertEquals(1, sequencer.next(TimeSignature.FOUR_FOUR, true, 0).beat)
        assertEquals(2, sequencer.next(TimeSignature.THREE_FOUR, true, 1).beat)
        assertEquals(3, sequencer.next(TimeSignature.THREE_FOUR, true, 2).beat)
        assertEquals(4, sequencer.next(TimeSignature.THREE_FOUR, true, 3).beat)

        val firstBeatOfNewMeasure = sequencer.next(TimeSignature.THREE_FOUR, true, 4)
        assertEquals(1, firstBeatOfNewMeasure.beat)
        assertEquals(TimeSignature.THREE_FOUR, firstBeatOfNewMeasure.timeSignature)
        assertTrue(firstBeatOfNewMeasure.isAccent)
    }

    @Test
    fun `six eight emits six eighth note positions`() {
        val sequencer = BeatSequencer(TimeSignature.SIX_EIGHT)
        val beats = List(8) {
            sequencer.next(TimeSignature.SIX_EIGHT, true, it.toLong())
        }

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 1, 2), beats.map { it.beat })
        assertEquals(listOf(true, false, false, false, false, false, true, false), beats.map { it.isAccent })
    }

    @Test
    fun `disabled accent uses regular click for every beat`() {
        val sequencer = BeatSequencer(TimeSignature.TWO_FOUR)
        assertFalse(sequencer.next(TimeSignature.TWO_FOUR, false, 0).isAccent)
        assertFalse(sequencer.next(TimeSignature.TWO_FOUR, false, 1).isAccent)
        assertFalse(sequencer.next(TimeSignature.TWO_FOUR, false, 2).isAccent)
    }

    @Test
    fun `unknown stored signature restores four four default`() {
        assertEquals(TimeSignature.FOUR_FOUR, TimeSignature.fromStored("UNKNOWN"))
        assertEquals(TimeSignature.SIX_EIGHT, TimeSignature.fromStored("SIX_EIGHT"))
    }
}
