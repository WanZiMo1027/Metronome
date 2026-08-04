package com.yuntian.metronome.metronome

const val MIN_BPM = 30
const val MAX_BPM = 300
const val DEFAULT_BPM = 120
const val MIN_CUSTOM_DIVISIONS = 1
const val MAX_CUSTOM_DIVISIONS = 8

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

enum class PlaybackMode {
    PRESET,
    CUSTOM;

    companion object {
        fun fromStored(value: String?): PlaybackMode =
            entries.firstOrNull { it.name == value } ?: PRESET
    }
}

enum class CellSound {
    NORMAL,
    ACCENT,
    SILENT;

    fun next(): CellSound = when (this) {
        NORMAL -> ACCENT
        ACCENT -> SILENT
        SILENT -> NORMAL
    }

    companion object {
        fun fromStored(value: String?): CellSound =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

data class BeatPattern(
    val cells: List<CellSound> = listOf(CellSound.NORMAL),
) {
    val divisionCount: Int
        get() = cells.size

    fun sanitized(): BeatPattern {
        val safeCount = cells.size.coerceIn(MIN_CUSTOM_DIVISIONS, MAX_CUSTOM_DIVISIONS)
        return BeatPattern(cells.take(safeCount).ifEmpty { listOf(CellSound.NORMAL) })
    }

    companion object {
        fun normal(divisions: Int = 1): BeatPattern = BeatPattern(
            List(divisions.coerceIn(MIN_CUSTOM_DIVISIONS, MAX_CUSTOM_DIVISIONS)) {
                CellSound.NORMAL
            },
        )
    }
}

fun defaultCustomPattern(timeSignature: TimeSignature): List<BeatPattern> =
    List(timeSignature.beatsPerMeasure) { beatIndex ->
        BeatPattern(
            listOf(if (beatIndex == 0) CellSound.ACCENT else CellSound.NORMAL),
        )
    }

fun sanitizeCustomPattern(
    pattern: List<BeatPattern>,
    timeSignature: TimeSignature,
): List<BeatPattern> {
    if (pattern.isEmpty()) return defaultCustomPattern(timeSignature)
    val retained = pattern
        .take(timeSignature.beatsPerMeasure)
        .map(BeatPattern::sanitized)
    return retained + List(timeSignature.beatsPerMeasure - retained.size) {
        BeatPattern.normal()
    }
}

data class CustomPreset(
    val id: String,
    val name: String,
    val bpm: Int,
    val timeSignature: TimeSignature,
    val beats: List<BeatPattern>,
) {
    fun sanitized(): CustomPreset = copy(
        name = name.trim(),
        bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
        beats = sanitizeCustomPattern(beats, timeSignature),
    )
}

enum class AccentLevel {
    DOWNBEAT,
    BEAT,
    SUBDIVISION,
    SILENT,
}

data class MetronomeSettings(
    val bpm: Int = DEFAULT_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val subdivision: Subdivision = Subdivision.QUARTER,
    val step: Int = 1,
    val accentEnabled: Boolean = true,
    val playbackMode: PlaybackMode = PlaybackMode.PRESET,
    val customPattern: List<BeatPattern> = defaultCustomPattern(timeSignature),
) {
    fun sanitized(): MetronomeSettings = copy(
        bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
        step = if (step == 5) 5 else 1,
        customPattern = sanitizeCustomPattern(customPattern, timeSignature),
    )
}

data class PulseEvent(
    val beat: Int,
    val subdivisionIndex: Int,
    val subdivision: Subdivision,
    val stepWeights: List<Int>,
    val timeSignature: TimeSignature,
    val playbackMode: PlaybackMode,
    val activeCustomPattern: List<BeatPattern>,
    val bpm: Int,
    val accentLevel: AccentLevel,
    val scheduledAtNanos: Long,
) {
    val subdivisionCount: Int
        get() = stepWeights.size
}

data class MetronomeUiState(
    val bpm: Int = DEFAULT_BPM,
    val activeBpm: Int = bpm,
    val step: Int = 1,
    val activeTimeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val pendingTimeSignature: TimeSignature? = null,
    val activeSubdivision: Subdivision = Subdivision.QUARTER,
    val pendingSubdivision: Subdivision? = null,
    val accentEnabled: Boolean = true,
    val playbackMode: PlaybackMode = PlaybackMode.PRESET,
    val activePlaybackMode: PlaybackMode = playbackMode,
    val customPattern: List<BeatPattern> = defaultCustomPattern(activeTimeSignature),
    val activeCustomPattern: List<BeatPattern> = customPattern,
    val customPresets: List<CustomPreset> = emptyList(),
    val isPlaying: Boolean = false,
    val currentBeat: Int? = null,
    val currentSubdivisionIndex: Int? = null,
    val currentSubdivisionCount: Int? = null,
    val errorMessage: String? = null,
) {
    val selectedTimeSignature: TimeSignature
        get() = pendingTimeSignature ?: activeTimeSignature

    val selectedSubdivision: Subdivision
        get() = pendingSubdivision ?: activeSubdivision

    val hasPendingConfiguration: Boolean
        get() = isPlaying && (
            bpm != activeBpm ||
                selectedTimeSignature != activeTimeSignature ||
                playbackMode != activePlaybackMode ||
                (playbackMode == PlaybackMode.PRESET &&
                    selectedSubdivision != activeSubdivision) ||
                (playbackMode == PlaybackMode.CUSTOM &&
                    customPattern != activeCustomPattern)
            )

    fun playbackSettings(): MetronomeSettings = MetronomeSettings(
        bpm = bpm,
        timeSignature = selectedTimeSignature,
        subdivision = selectedSubdivision,
        step = step,
        accentEnabled = accentEnabled,
        playbackMode = playbackMode,
        customPattern = sanitizeCustomPattern(customPattern, selectedTimeSignature),
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
    ): Long = stepDurationNanos(bpm, subdivision.stepWeights, stepIndex)

    fun stepDurationNanos(
        bpm: Int,
        stepWeights: List<Int>,
        stepIndex: Int,
    ): Long {
        require(stepWeights.isNotEmpty())
        require(stepWeights.all { it > 0 })
        require(stepIndex in stepWeights.indices)
        val beatDuration = intervalNanos(bpm)
        val totalWeight = stepWeights.sum()
        val startWeight = stepWeights.take(stepIndex).sum()
        val endWeight = startWeight + stepWeights[stepIndex]
        val startOffset = beatDuration * startWeight / totalWeight
        val endOffset = beatDuration * endWeight / totalWeight
        return endOffset - startOffset
    }

    fun nextDeadlineNanos(
        lastScheduledAtNanos: Long,
        bpm: Int,
        subdivision: Subdivision = Subdivision.QUARTER,
        stepIndex: Int = 0,
    ): Long = lastScheduledAtNanos + stepDurationNanos(bpm, subdivision, stepIndex)

    fun nextDeadlineNanos(
        lastScheduledAtNanos: Long,
        bpm: Int,
        stepWeights: List<Int>,
        stepIndex: Int,
    ): Long = lastScheduledAtNanos + stepDurationNanos(bpm, stepWeights, stepIndex)

    fun resolveScheduledAtNanos(
        lastScheduledAtNanos: Long,
        nowNanos: Long,
        bpm: Int,
        subdivision: Subdivision = Subdivision.QUARTER,
        stepIndex: Int = 0,
    ): Long = resolveScheduledAtNanos(
        lastScheduledAtNanos = lastScheduledAtNanos,
        nowNanos = nowNanos,
        bpm = bpm,
        stepWeights = subdivision.stepWeights,
        stepIndex = stepIndex,
    )

    fun resolveScheduledAtNanos(
        lastScheduledAtNanos: Long,
        nowNanos: Long,
        bpm: Int,
        stepWeights: List<Int>,
        stepIndex: Int,
    ): Long {
        val interval = stepDurationNanos(bpm, stepWeights, stepIndex)
        val requestedDeadline = lastScheduledAtNanos + interval
        return if (nowNanos - requestedDeadline > interval) nowNanos else requestedDeadline
    }
}

internal class PulseSequencer(initialSettings: MetronomeSettings) {
    private var activeSettings: MetronomeSettings = initialSettings.sanitized()

    val activeTimeSignature: TimeSignature
        get() = activeSettings.timeSignature

    val activeSubdivision: Subdivision
        get() = activeSettings.subdivision

    val activePlaybackMode: PlaybackMode
        get() = activeSettings.playbackMode

    private var currentBeat = 0
    private var currentSubdivisionIndex = -1

    constructor(
        initialSignature: TimeSignature,
        initialSubdivision: Subdivision = Subdivision.QUARTER,
    ) : this(
        initialSettings = MetronomeSettings(
            timeSignature = initialSignature,
            subdivision = initialSubdivision,
        ),
    )

    fun next(
        requestedSettings: MetronomeSettings,
        scheduledAtNanos: Long,
    ): PulseEvent {
        if (currentBeat == 0) {
            currentBeat = 1
            currentSubdivisionIndex = 0
        } else {
            val currentWeights = stepWeightsFor(activeSettings, currentBeat)
            if (currentSubdivisionIndex < currentWeights.lastIndex) {
                currentSubdivisionIndex += 1
            } else {
                currentSubdivisionIndex = 0
                if (currentBeat >= activeSettings.timeSignature.beatsPerMeasure) {
                    activeSettings = requestedSettings.sanitized()
                    currentBeat = 1
                } else {
                    currentBeat += 1
                }
            }
        }

        val stepWeights = stepWeightsFor(activeSettings, currentBeat)
        val accentLevel = accentFor(
            settings = activeSettings,
            beat = currentBeat,
            subdivisionIndex = currentSubdivisionIndex,
        )

        return PulseEvent(
            beat = currentBeat,
            subdivisionIndex = currentSubdivisionIndex,
            subdivision = activeSettings.subdivision,
            stepWeights = stepWeights,
            timeSignature = activeSettings.timeSignature,
            playbackMode = activeSettings.playbackMode,
            activeCustomPattern = activeSettings.customPattern,
            bpm = activeSettings.bpm,
            accentLevel = accentLevel,
            scheduledAtNanos = scheduledAtNanos,
        )
    }

    fun next(
        requestedSignature: TimeSignature,
        requestedSubdivision: Subdivision = activeSubdivision,
        accentEnabled: Boolean,
        scheduledAtNanos: Long,
    ): PulseEvent {
        // Preserve the legacy API's immediate accent toggle while signature and
        // subdivision changes still wait for the next measure.
        activeSettings = activeSettings.copy(accentEnabled = accentEnabled)
        return next(
            requestedSettings = activeSettings.copy(
                timeSignature = requestedSignature,
                subdivision = requestedSubdivision,
            ),
            scheduledAtNanos = scheduledAtNanos,
        )
    }

    private fun stepWeightsFor(settings: MetronomeSettings, beat: Int): List<Int> =
        when (settings.playbackMode) {
            PlaybackMode.PRESET -> settings.subdivision.stepWeights
            PlaybackMode.CUSTOM -> List(settings.customPattern[beat - 1].divisionCount) { 1 }
        }

    private fun accentFor(
        settings: MetronomeSettings,
        beat: Int,
        subdivisionIndex: Int,
    ): AccentLevel = when (settings.playbackMode) {
        PlaybackMode.PRESET -> when {
            subdivisionIndex > 0 -> AccentLevel.SUBDIVISION
            beat == 1 && settings.accentEnabled -> AccentLevel.DOWNBEAT
            else -> AccentLevel.BEAT
        }

        PlaybackMode.CUSTOM -> when (
            settings.customPattern[beat - 1].cells[subdivisionIndex]
        ) {
            CellSound.ACCENT -> AccentLevel.DOWNBEAT
            CellSound.SILENT -> AccentLevel.SILENT
            CellSound.NORMAL -> if (subdivisionIndex == 0) {
                AccentLevel.BEAT
            } else {
                AccentLevel.SUBDIVISION
            }
        }
    }
}
