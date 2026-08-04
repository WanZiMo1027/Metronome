package com.yuntian.metronome.metronome

const val MIN_BPM = 30
const val MAX_BPM = 300
const val DEFAULT_BPM = 120

enum class TimeSignature(
    val beatsPerMeasure: Int,
    val denominator: Int,
    val label: String,
) {
    TWO_FOUR(2, 4, "2/4"),
    THREE_FOUR(3, 4, "3/4"),
    FOUR_FOUR(4, 4, "4/4"),
    SIX_EIGHT(6, 8, "6/8");

    companion object {
        fun fromStored(value: String?): TimeSignature =
            entries.firstOrNull { it.name == value } ?: FOUR_FOUR
    }
}

enum class Subdivision(
    val stepWeights: List<Int>,
    val accessibilityLabel: String,
) {
    QUARTER(listOf(1), "四分音符"),
    EIGHTH(listOf(1, 1), "八分音符"),
    EIGHTH_TRIPLET(listOf(1, 1, 1), "八分三连音"),
    SIXTEENTH(listOf(1, 1, 1, 1), "十六分音符"),
    SWING_LONG_SHORT(listOf(3, 1), "Swing 长短"),
    SWING_SHORT_LONG(listOf(1, 3), "Swing 短长");

    val totalWeight: Int = stepWeights.sum()
    val stepCount: Int = stepWeights.size

    companion object {
        fun fromStored(value: String?): Subdivision =
            entries.firstOrNull { it.name == value } ?: QUARTER
    }
}

enum class AccentLevel {
    DOWNBEAT,
    BEAT,
    SUBDIVISION,
}

data class MetronomeSettings(
    val bpm: Int = DEFAULT_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val subdivision: Subdivision = Subdivision.QUARTER,
    val step: Int = 1,
    val accentEnabled: Boolean = true,
) {
    fun sanitized(): MetronomeSettings = copy(
        bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
        step = if (step == 5) 5 else 1,
    )
}

data class PulseEvent(
    val beat: Int,
    val subdivisionIndex: Int,
    val subdivision: Subdivision,
    val timeSignature: TimeSignature,
    val accentLevel: AccentLevel,
    val scheduledAtNanos: Long,
) {
    val subdivisionCount: Int
        get() = subdivision.stepCount
}

data class MetronomeUiState(
    val bpm: Int = DEFAULT_BPM,
    val step: Int = 1,
    val activeTimeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val pendingTimeSignature: TimeSignature? = null,
    val activeSubdivision: Subdivision = Subdivision.QUARTER,
    val pendingSubdivision: Subdivision? = null,
    val accentEnabled: Boolean = true,
    val isPlaying: Boolean = false,
    val currentBeat: Int? = null,
    val currentSubdivisionIndex: Int? = null,
    val errorMessage: String? = null,
) {
    val selectedTimeSignature: TimeSignature
        get() = pendingTimeSignature ?: activeTimeSignature

    val selectedSubdivision: Subdivision
        get() = pendingSubdivision ?: activeSubdivision

    fun playbackSettings(): MetronomeSettings = MetronomeSettings(
        bpm = bpm,
        timeSignature = selectedTimeSignature,
        subdivision = selectedSubdivision,
        step = step,
        accentEnabled = accentEnabled,
    )
}

fun parseBpmInput(value: String): Int? =
    value.trim().toIntOrNull()?.takeIf { it in MIN_BPM..MAX_BPM }

internal object MetronomeTiming {
    fun intervalNanos(bpm: Int): Long = 60_000_000_000L / bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun stepDurationNanos(
        bpm: Int,
        subdivision: Subdivision,
        stepIndex: Int,
    ): Long {
        require(stepIndex in subdivision.stepWeights.indices)
        val beatDuration = intervalNanos(bpm)
        var startWeight = 0
        for (index in 0 until stepIndex) {
            startWeight += subdivision.stepWeights[index]
        }
        val endWeight = startWeight + subdivision.stepWeights[stepIndex]
        val startOffset = beatDuration * startWeight / subdivision.totalWeight
        val endOffset = beatDuration * endWeight / subdivision.totalWeight
        return endOffset - startOffset
    }

    fun nextDeadlineNanos(
        lastScheduledAtNanos: Long,
        bpm: Int,
        subdivision: Subdivision = Subdivision.QUARTER,
        stepIndex: Int = 0,
    ): Long = lastScheduledAtNanos + stepDurationNanos(bpm, subdivision, stepIndex)

    fun resolveScheduledAtNanos(
        lastScheduledAtNanos: Long,
        nowNanos: Long,
        bpm: Int,
        subdivision: Subdivision = Subdivision.QUARTER,
        stepIndex: Int = 0,
    ): Long {
        val interval = stepDurationNanos(bpm, subdivision, stepIndex)
        val requestedDeadline = lastScheduledAtNanos + interval
        return if (nowNanos - requestedDeadline > interval) nowNanos else requestedDeadline
    }
}

internal class PulseSequencer(
    initialSignature: TimeSignature,
    initialSubdivision: Subdivision = Subdivision.QUARTER,
) {
    var activeTimeSignature: TimeSignature = initialSignature
        private set

    var activeSubdivision: Subdivision = initialSubdivision
        private set

    private var currentBeat = 0
    private var currentSubdivisionIndex = -1

    fun next(
        requestedSignature: TimeSignature,
        requestedSubdivision: Subdivision = activeSubdivision,
        accentEnabled: Boolean,
        scheduledAtNanos: Long,
    ): PulseEvent {
        if (currentBeat == 0) {
            currentBeat = 1
            currentSubdivisionIndex = 0
        } else if (currentSubdivisionIndex < activeSubdivision.stepCount - 1) {
            currentSubdivisionIndex += 1
        } else {
            currentSubdivisionIndex = 0
            if (currentBeat >= activeTimeSignature.beatsPerMeasure) {
                activeTimeSignature = requestedSignature
                activeSubdivision = requestedSubdivision
                currentBeat = 1
            } else {
                currentBeat += 1
            }
        }

        val accentLevel = when {
            currentSubdivisionIndex > 0 -> AccentLevel.SUBDIVISION
            currentBeat == 1 && accentEnabled -> AccentLevel.DOWNBEAT
            else -> AccentLevel.BEAT
        }

        return PulseEvent(
            beat = currentBeat,
            subdivisionIndex = currentSubdivisionIndex,
            subdivision = activeSubdivision,
            timeSignature = activeTimeSignature,
            accentLevel = accentLevel,
            scheduledAtNanos = scheduledAtNanos,
        )
    }
}
