package com.yuntian.metronome.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementAudioExportTest {
    private val silentClips = AudioClipFrameLengths(
        downbeat = 0,
        otherBeat = 0,
        numbers = List(11) { 0 },
    )

    @Test
    fun `complete export ends after the last change measure without a loop pulse`() {
        val oneFour = ArrangementMeter(1, 4)
        val twoFour = ArrangementMeter(2, 4)
        val plan = createArrangementExportPlan(
            rawChanges = listOf(
                ArrangementChange(1, 120, oneFour, defaultArrangementPattern(oneFour)),
                ArrangementChange(3, 60, twoFour, defaultArrangementPattern(twoFour)),
            ),
            options = ArrangementExportOptions(false, false),
            clips = silentClips,
        )

        assertEquals(4, plan.pulseCount)
        assertEquals(132_300L, plan.arrangementEndFrame)
        assertEquals(plan.arrangementEndFrame, plan.totalFrames)
    }

    @Test
    fun `count in adds exactly one first meter measure`() {
        val meter = ArrangementMeter(3, 4)
        val change = ArrangementChange(1, 120, meter, defaultArrangementPattern(meter))
        val withoutCountIn = createArrangementExportPlan(
            listOf(change),
            ArrangementExportOptions(false, false),
            silentClips,
        )
        val withCountIn = createArrangementExportPlan(
            listOf(change),
            ArrangementExportOptions(true, false),
            silentClips,
        )

        assertEquals(3, withoutCountIn.pulseCount)
        assertEquals(6, withCountIn.pulseCount)
        assertEquals(withoutCountIn.arrangementEndFrame * 2, withCountIn.arrangementEndFrame)
    }

    @Test
    fun `meter number cues can be omitted or drained past the arrangement boundary`() {
        val firstMeter = ArrangementMeter(1, 4)
        val changedMeter = ArrangementMeter(1, 8)
        val changes = listOf(
            ArrangementChange(1, 300, firstMeter, defaultArrangementPattern(firstMeter)),
            ArrangementChange(2, 300, changedMeter, defaultArrangementPattern(changedMeter)),
        )
        val clips = AudioClipFrameLengths(
            downbeat = 4_410,
            otherBeat = 4_410,
            numbers = List(11) { 44_100 },
        )

        val withoutCues = createArrangementExportPlan(
            changes,
            ArrangementExportOptions(false, false),
            clips,
        )
        val withCues = createArrangementExportPlan(
            changes,
            ArrangementExportOptions(false, true),
            clips,
        )

        assertEquals(17_640L, withoutCues.totalFrames)
        assertEquals(66_150L, withCues.totalFrames)
        assertTrue(withCues.totalFrames > withCues.arrangementEndFrame)
    }

    @Test
    fun `subdivisions and tempo changes keep the continuous frame remainder`() {
        val meter = ArrangementMeter(1, 4)
        val changes = listOf(
            ArrangementChange(1, 137, meter, listOf(BeatPattern.normal(3))),
            ArrangementChange(2, 211, meter, listOf(BeatPattern.normal(7))),
        )
        val plan = createArrangementExportPlan(
            changes,
            ArrangementExportOptions(false, false),
            silentClips,
        )
        val expected = METRONOME_PCM_SAMPLE_RATE * 60.0 / 137.0 +
            METRONOME_PCM_SAMPLE_RATE * 60.0 / 211.0

        assertEquals(10, plan.pulseCount)
        assertTrue(kotlin.math.abs(plan.arrangementEndFrame - expected) < 1.0)
    }

    @Test
    fun `sanitized extreme arrangement can be identified as longer than sixty minutes`() {
        val meter = ArrangementMeter(9, 4)
        val plan = createArrangementExportPlan(
            rawChanges = listOf(
                ArrangementChange(1, 30, meter, defaultArrangementPattern(meter)),
                ArrangementChange(201, 30, meter, defaultArrangementPattern(meter)),
            ),
            options = ArrangementExportOptions(false, false),
            clips = silentClips,
        )

        assertTrue(plan.durationSeconds > ARRANGEMENT_EXPORT_MAX_DURATION_SECONDS)
    }

    @Test(expected = ArrangementExportTooLongException::class)
    fun `planner aborts once the sixty minute frame limit is exceeded`() {
        val meter = ArrangementMeter(9, 4)

        createArrangementExportPlan(
            rawChanges = listOf(
                ArrangementChange(1, 30, meter, defaultArrangementPattern(meter)),
                ArrangementChange(201, 30, meter, defaultArrangementPattern(meter)),
            ),
            options = ArrangementExportOptions(false, false),
            clips = silentClips,
            maximumFrames = ARRANGEMENT_EXPORT_MAX_DURATION_SECONDS * METRONOME_PCM_SAMPLE_RATE,
        )
    }
}
