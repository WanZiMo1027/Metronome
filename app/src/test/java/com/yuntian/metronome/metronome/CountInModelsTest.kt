package com.yuntian.metronome.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountInModelsTest {
    @Test
    fun `preset count in emits one complete measure before playback`() {
        val settings = MetronomeSettings(
            timeSignature = TimeSignature.TWO_FOUR,
            subdivision = Subdivision.EIGHTH,
            countInEnabled = true,
        )
        val sequencer = PulseSequencer(settings)

        val events = List(5) { sequencer.next(settings, 0L) }

        assertEquals(listOf(1, 1, 2, 2, 1), events.map { it.beat })
        assertEquals(listOf(0, 1, 0, 1, 0), events.map { it.subdivisionIndex })
        assertEquals(listOf(true, true, true, true, false), events.map { it.isCountIn })
        assertEquals(
            listOf(
                AccentLevel.DOWNBEAT,
                AccentLevel.SUBDIVISION,
                AccentLevel.BEAT,
                AccentLevel.SUBDIVISION,
            ),
            events.take(4).map { it.accentLevel },
        )
    }

    @Test
    fun `custom count in preserves subdivisions accents and silence`() {
        val settings = MetronomeSettings(
            timeSignature = TimeSignature.TWO_FOUR,
            countInEnabled = true,
            playbackMode = PlaybackMode.CUSTOM,
            customPattern = listOf(
                BeatPattern(listOf(CellSound.ACCENT, CellSound.SILENT)),
                BeatPattern(listOf(CellSound.NORMAL)),
            ),
        )
        val sequencer = PulseSequencer(settings)

        val events = List(4) { sequencer.next(settings, 0L) }

        assertEquals(listOf(true, true, true, false), events.map { it.isCountIn })
        assertEquals(
            listOf(AccentLevel.DOWNBEAT, AccentLevel.SILENT, AccentLevel.BEAT),
            events.take(3).map { it.accentLevel },
        )
        assertEquals(listOf(2, 2, 1), events.take(3).map { it.subdivisionCount })
        assertEquals(1, events.last().beat)
    }

    @Test
    fun `configuration requested during count in starts on the first playback measure`() {
        val initial = MetronomeSettings(
            timeSignature = TimeSignature.TWO_FOUR,
            subdivision = Subdivision.EIGHTH,
            countInEnabled = true,
        )
        val requested = initial.copy(
            timeSignature = TimeSignature.THREE_FOUR,
            subdivision = Subdivision.EIGHTH_TRIPLET,
        )
        val sequencer = PulseSequencer(initial)

        val countIn = List(4) { sequencer.next(requested, 0L) }
        val playback = sequencer.next(requested, 0L)

        assertTrue(countIn.all { it.isCountIn })
        assertTrue(countIn.all { it.timeSignature == TimeSignature.TWO_FOUR })
        assertTrue(countIn.all { it.subdivision == Subdivision.EIGHTH })
        assertFalse(playback.isCountIn)
        assertEquals(1, playback.beat)
        assertEquals(TimeSignature.THREE_FOUR, playback.timeSignature)
        assertEquals(Subdivision.EIGHTH_TRIPLET, playback.subdivision)
    }

    @Test
    fun `arrangement count in copies the configuration active at a selected measure`() {
        val firstMeter = ArrangementMeter(2, 4)
        val targetMeter = ArrangementMeter(3, 8)
        val targetPattern = listOf(
            BeatPattern(listOf(CellSound.ACCENT, CellSound.SILENT)),
            BeatPattern.normal(),
            BeatPattern.normal(3),
        )
        val changes = listOf(
            ArrangementChange(1, 120, firstMeter, defaultArrangementPattern(firstMeter)),
            ArrangementChange(4, 180, targetMeter, targetPattern),
        )
        val sequencer = ArrangementSequencer(changes, initialMeasure = 4, countInEnabled = true)

        val countIn = List(6) { sequencer.next(0L) }
        val playback = sequencer.next(0L)

        assertTrue(countIn.all { it.isCountIn && it.measureNumber == 4 })
        assertTrue(countIn.all { it.arrangementRowIndex == 1 })
        assertTrue(countIn.all { it.bpm == 180 && it.arrangementMeter == targetMeter })
        assertEquals(AccentLevel.SILENT, countIn[1].accentLevel)
        assertTrue(countIn.all { it.numberCues.isEmpty() })
        assertFalse(playback.isCountIn)
        assertEquals(4, playback.measureNumber)
        assertEquals(1, playback.beat)

        val betweenChanges = ArrangementSequencer(
            changes,
            initialMeasure = 3,
            countInEnabled = true,
        ).next(0L)
        assertTrue(betweenChanges.isCountIn)
        assertEquals(3, betweenChanges.measureNumber)
        assertEquals(0, betweenChanges.arrangementRowIndex)
        assertEquals(firstMeter, betweenChanges.arrangementMeter)
    }

    @Test
    fun `arrangement count in runs once and does not repeat when looping`() {
        val meter = ArrangementMeter(1, 4)
        val sequencer = ArrangementSequencer(
            rawChanges = listOf(
                ArrangementChange(1, 120, meter, defaultArrangementPattern(meter)),
                ArrangementChange(2, 180, meter, defaultArrangementPattern(meter)),
            ),
            countInEnabled = true,
        )

        val events = List(4) { sequencer.next(0L) }

        assertEquals(listOf(true, false, false, false), events.map { it.isCountIn })
        assertEquals(listOf(1, 1, 2, 1), events.map { it.measureNumber })
    }
}
