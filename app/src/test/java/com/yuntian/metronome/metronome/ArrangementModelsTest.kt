package com.yuntian.metronome.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementModelsTest {
    @Test
    fun `meter supports every requested numerator and denominator`() {
        for (numerator in MIN_ARRANGEMENT_NUMERATOR..MAX_ARRANGEMENT_NUMERATOR) {
            for (denominator in MIN_ARRANGEMENT_DENOMINATOR..MAX_ARRANGEMENT_DENOMINATOR) {
                val meter = ArrangementMeter(numerator, denominator).sanitized()
                assertEquals(numerator, meter.numerator)
                assertEquals(denominator, meter.denominator)
                assertEquals("$numerator/$denominator", meter.label)
            }
        }
        assertEquals(ArrangementMeter(1, 8), ArrangementMeter(-3, 20).sanitized())
    }

    @Test
    fun `first change defaults to 120 four four with first beat accent`() {
        val first = appendArrangementChange(emptyList()).single()

        assertEquals(1, first.startMeasure)
        assertEquals(120, first.bpm)
        assertEquals(ArrangementMeter(4, 4), first.meter)
        assertEquals(4, first.beats.size)
        assertEquals(CellSound.ACCENT, first.beats.first().cells.first())
        assertTrue(first.beats.drop(1).all { it.cells == listOf(CellSound.NORMAL) })
    }

    @Test
    fun `appended change copies complete previous configuration`() {
        val previous = ArrangementChange(
            startMeasure = 7,
            bpm = 173,
            meter = ArrangementMeter(3, 7),
            beats = listOf(
                BeatPattern(listOf(CellSound.ACCENT, CellSound.SILENT)),
                BeatPattern.normal(3),
                BeatPattern(listOf(CellSound.SILENT)),
            ),
        )

        val changes = appendArrangementChange(listOf(previous))

        assertEquals(1, changes.first().startMeasure)
        assertEquals(2, changes.last().startMeasure)
        assertEquals(changes.first().copy(startMeasure = 2), changes.last())
    }

    @Test
    fun `resizing a beat preserves retained cells and fills new cells normally`() {
        val beat = BeatPattern(listOf(CellSound.ACCENT, CellSound.SILENT))

        assertEquals(
            listOf(CellSound.ACCENT, CellSound.SILENT, CellSound.NORMAL, CellSound.NORMAL),
            resizeBeatPattern(beat, 4).cells,
        )
        assertEquals(listOf(CellSound.ACCENT), resizeBeatPattern(beat, 1).cells)
    }

    @Test
    fun `meter resizing preserves overlapping beats and fills new beats`() {
        val original = listOf(
            BeatPattern(listOf(CellSound.ACCENT, CellSound.SILENT)),
            BeatPattern.normal(3),
        )

        val expanded = sanitizeArrangementPattern(original, ArrangementMeter(5, 8))
        assertEquals(original, expanded.take(2))
        assertEquals(List(3) { BeatPattern.normal() }, expanded.drop(2))
        assertEquals(original.take(1), sanitizeArrangementPattern(original, ArrangementMeter(1, 8)))
    }

    @Test
    fun `start measure validation and deletion preserve an ordered timeline`() {
        val changes = listOf(
            ArrangementChange(startMeasure = 1),
            ArrangementChange(startMeasure = 5),
            ArrangementChange(startMeasure = 10),
        )

        assertTrue(isValidArrangementStartMeasure(changes, 1, 6))
        assertTrue(isValidArrangementStartMeasure(changes, 1, 10))
        assertTrue(!isValidArrangementStartMeasure(changes, 1, 1))
        assertTrue(!isValidArrangementStartMeasure(changes, 0, 2))
        assertEquals(listOf(1, 9), removeArrangementChange(changes, 1).map { it.startMeasure })
        assertEquals(listOf(1, 6), removeArrangementChange(changes, 0).map { it.startMeasure })
        assertEquals(listOf(1, 5), removeArrangementChange(changes, 2).map { it.startMeasure })
        assertEquals(emptyList<ArrangementChange>(), removeArrangementChange(listOf(changes[0]), 0))
    }

    @Test
    fun `inserting after a row copies it and shifts the complete suffix`() {
        val changes = listOf(
            ArrangementChange(startMeasure = 1, bpm = 101),
            ArrangementChange(startMeasure = 5, bpm = 125),
            ArrangementChange(startMeasure = 10, bpm = 150),
        )

        val afterFirst = insertArrangementChangeAfter(changes, 0)
        assertEquals(listOf(1, 2, 6, 11), afterFirst.map { it.startMeasure })
        assertEquals(afterFirst[0].copy(startMeasure = 2), afterFirst[1])

        val afterMiddle = insertArrangementChangeAfter(changes, 1)
        assertEquals(listOf(1, 5, 6, 11), afterMiddle.map { it.startMeasure })
        assertEquals(afterMiddle[1].copy(startMeasure = 6), afterMiddle[2])

        assertEquals(
            listOf(1, 5, 10, 11),
            insertArrangementChangeAfter(changes, 2).map { it.startMeasure },
        )
    }

    @Test
    fun `editing a start measure shifts the selected row and suffix by one delta`() {
        val changes = listOf(
            ArrangementChange(startMeasure = 1),
            ArrangementChange(startMeasure = 5),
            ArrangementChange(startMeasure = 10),
        )

        assertEquals(
            listOf(1, 7, 12),
            shiftArrangementStartMeasure(changes, 1, 7).map { it.startMeasure },
        )
        assertEquals(
            listOf(1, 2, 7),
            shiftArrangementStartMeasure(changes, 1, 2).map { it.startMeasure },
        )
        assertEquals(
            listOf(1, 5, 10),
            shiftArrangementStartMeasure(changes, 1, 1).map { it.startMeasure },
        )
    }

    @Test
    fun `change point ranges resolve and last measure loops to one`() {
        val oneBeat = ArrangementMeter(1, 4)
        val changes = listOf(
            ArrangementChange(1, 100, oneBeat, defaultArrangementPattern(oneBeat)),
            ArrangementChange(5, 140, oneBeat, defaultArrangementPattern(oneBeat)),
            ArrangementChange(100, 180, oneBeat, defaultArrangementPattern(oneBeat)),
        )
        val sequencer = ArrangementSequencer(changes)
        val events = List(101) { sequencer.next(0L) }

        assertEquals((1..100).toList() + 1, events.map { it.measureNumber })
        assertEquals(List(4) { 0 } + List(95) { 1 } + 2 + 0, events.map { it.arrangementRowIndex })
        assertEquals(List(4) { 100 } + List(95) { 140 } + 180 + 100, events.map { it.bpm })
    }

    @Test
    fun `configuration changes only after the previous measure finishes`() {
        val firstMeter = ArrangementMeter(2, 4)
        val secondMeter = ArrangementMeter(3, 8)
        val firstPattern = listOf(BeatPattern.normal(2), BeatPattern.normal())
        val secondPattern = defaultArrangementPattern(secondMeter)
        val sequencer = ArrangementSequencer(
            listOf(
                ArrangementChange(1, 120, firstMeter, firstPattern),
                ArrangementChange(2, 190, secondMeter, secondPattern),
            ),
        )

        val firstMeasure = List(3) { sequencer.next(0L) }
        val secondMeasure = List(3) { sequencer.next(0L) }

        assertEquals(listOf(1, 1, 1), firstMeasure.map { it.measureNumber })
        assertEquals(listOf(1, 1, 2), firstMeasure.map { it.beat })
        assertTrue(firstMeasure.all { it.bpm == 120 && it.arrangementMeter == firstMeter })
        assertEquals(listOf(2, 2, 2), secondMeasure.map { it.measureNumber })
        assertEquals(listOf(1, 2, 3), secondMeasure.map { it.beat })
        assertTrue(secondMeasure.all { it.bpm == 190 && it.arrangementMeter == secondMeter })
    }

    @Test
    fun `arrangement can start from any sanitized measure and still loop to one`() {
        val firstMeter = ArrangementMeter(2, 4)
        val lastMeter = ArrangementMeter(3, 8)
        val changes = listOf(
            ArrangementChange(1, 120, firstMeter, defaultArrangementPattern(firstMeter)),
            ArrangementChange(4, 180, lastMeter, defaultArrangementPattern(lastMeter)),
        )

        val betweenChanges = ArrangementSequencer(changes, initialMeasure = 3)
        val firstBetweenEvent = betweenChanges.next(0L)
        assertEquals(3, firstBetweenEvent.measureNumber)
        assertEquals(0, firstBetweenEvent.arrangementRowIndex)
        assertEquals(1, firstBetweenEvent.beat)
        assertEquals(0, firstBetweenEvent.subdivisionIndex)
        assertEquals(120, firstBetweenEvent.bpm)
        assertEquals(firstMeter, firstBetweenEvent.arrangementMeter)

        val exactChange = ArrangementSequencer(changes, initialMeasure = 4).next(0L)
        assertEquals(4, exactChange.measureNumber)
        assertEquals(1, exactChange.arrangementRowIndex)
        assertEquals(180, exactChange.bpm)
        assertEquals(lastMeter, exactChange.arrangementMeter)
        assertTrue(exactChange.numberCues.isEmpty())

        val clampedLow = ArrangementSequencer(changes, initialMeasure = 0).next(0L)
        assertEquals(1, clampedLow.measureNumber)

        val clampedHigh = ArrangementSequencer(changes, initialMeasure = Int.MAX_VALUE)
        assertEquals(4, clampedHigh.next(0L).measureNumber)
        repeat(lastMeter.numerator - 1) { clampedHigh.next(0L) }
        assertEquals(1, clampedHigh.next(0L).measureNumber)
    }
}
