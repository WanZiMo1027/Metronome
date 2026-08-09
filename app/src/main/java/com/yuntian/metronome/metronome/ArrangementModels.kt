package com.yuntian.metronome.metronome

const val MIN_ARRANGEMENT_NUMERATOR = 1
const val MAX_ARRANGEMENT_NUMERATOR = 9
const val MIN_ARRANGEMENT_DENOMINATOR = 1
const val MAX_ARRANGEMENT_DENOMINATOR = 8
const val DENOMINATOR_CUE_DELAY_MILLIS = 300L

data class ArrangementMeter(
    val numerator: Int = 4,
    val denominator: Int = 4,
) {
    val label: String
        get() = "$numerator/$denominator"

    fun sanitized(): ArrangementMeter = copy(
        numerator = numerator.coerceIn(MIN_ARRANGEMENT_NUMERATOR, MAX_ARRANGEMENT_NUMERATOR),
        denominator = denominator.coerceIn(MIN_ARRANGEMENT_DENOMINATOR, MAX_ARRANGEMENT_DENOMINATOR),
    )
}

data class ArrangementChange(
    val startMeasure: Int = 1,
    val bpm: Int = DEFAULT_BPM,
    val meter: ArrangementMeter = ArrangementMeter(),
    val beats: List<BeatPattern> = defaultArrangementPattern(meter),
) {
    fun sanitized(): ArrangementChange {
        val safeMeter = meter.sanitized()
        return copy(
            startMeasure = startMeasure.coerceAtLeast(1),
            bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
            meter = safeMeter,
            beats = sanitizeArrangementPattern(beats, safeMeter),
        )
    }
}

data class ArrangementPreset(
    val id: String,
    val name: String,
    val changes: List<ArrangementChange>,
) {
    fun sanitized(): ArrangementPreset = copy(
        name = name.trim(),
        changes = sanitizeArrangementChanges(changes),
    )
}

data class ArrangementUiState(
    val changes: List<ArrangementChange> = emptyList(),
    val presets: List<ArrangementPreset> = emptyList(),
    val selectedRowIndex: Int? = null,
    val playbackStartMeasure: Int = 1,
    val countInEnabled: Boolean = false,
    val isPlaying: Boolean = false,
    val isCountIn: Boolean = false,
    val currentMeasure: Int? = null,
    val currentRowIndex: Int? = null,
    val currentBeat: Int? = null,
    val currentSubdivisionIndex: Int? = null,
    val currentSubdivisionCount: Int? = null,
    val exportState: ArrangementExportState = ArrangementExportState.Idle,
    val errorMessage: String? = null,
)

data class ArrangementExportOptions(
    val includeCountIn: Boolean,
    val includeNumberCues: Boolean,
)

sealed interface ArrangementExportState {
    data object Idle : ArrangementExportState
    data object ChoosingDestination : ArrangementExportState
    data class Running(val progress: Float) : ArrangementExportState
    data object Success : ArrangementExportState
    data class Failure(val message: String) : ArrangementExportState
}

val ArrangementExportState.isBusy: Boolean
    get() = this is ArrangementExportState.ChoosingDestination ||
        this is ArrangementExportState.Running

fun defaultArrangementPattern(meter: ArrangementMeter): List<BeatPattern> =
    List(meter.sanitized().numerator) { beatIndex ->
        BeatPattern(
            listOf(if (beatIndex == 0) CellSound.ACCENT else CellSound.NORMAL),
        )
    }

fun sanitizeArrangementPattern(
    pattern: List<BeatPattern>,
    meter: ArrangementMeter,
): List<BeatPattern> {
    val beatCount = meter.sanitized().numerator
    if (pattern.isEmpty()) return defaultArrangementPattern(meter)
    val retained = pattern.take(beatCount).map(BeatPattern::sanitized)
    return retained + List(beatCount - retained.size) { BeatPattern.normal() }
}

fun sanitizeArrangementChanges(changes: List<ArrangementChange>): List<ArrangementChange> {
    if (changes.isEmpty()) return emptyList()
    val sorted = changes
        .map(ArrangementChange::sanitized)
        .sortedBy(ArrangementChange::startMeasure)
        .distinctBy(ArrangementChange::startMeasure)
    return sorted.mapIndexed { index, change ->
        if (index == 0) change.copy(startMeasure = 1) else change
    }
}

fun appendArrangementChange(changes: List<ArrangementChange>): List<ArrangementChange> {
    val safe = sanitizeArrangementChanges(changes)
    if (safe.isEmpty()) return listOf(ArrangementChange())
    return insertArrangementChangeAfter(safe, safe.lastIndex)
}

fun insertArrangementChangeAfter(
    changes: List<ArrangementChange>,
    rowIndex: Int,
): List<ArrangementChange> {
    val safe = sanitizeArrangementChanges(changes)
    if (safe.isEmpty()) return listOf(ArrangementChange())
    if (rowIndex !in safe.indices) return safe
    if (safe.drop(rowIndex).any { it.startMeasure == Int.MAX_VALUE }) return safe

    val source = safe[rowIndex]
    val updated = safe.mapIndexed { index, change ->
        if (index > rowIndex) change.copy(startMeasure = change.startMeasure + 1) else change
    }.toMutableList()
    updated.add(rowIndex + 1, source.copy(startMeasure = source.startMeasure + 1))
    return updated
}

fun isValidArrangementStartMeasure(
    changes: List<ArrangementChange>,
    rowIndex: Int,
    startMeasure: Int,
): Boolean {
    val safe = sanitizeArrangementChanges(changes)
    if (rowIndex !in safe.indices || rowIndex == 0) return false
    val minimum = safe[rowIndex - 1].startMeasure + 1
    if (startMeasure < minimum) return false
    val delta = startMeasure.toLong() - safe[rowIndex].startMeasure
    return safe.drop(rowIndex).all { change ->
        change.startMeasure.toLong() + delta in 1..Int.MAX_VALUE.toLong()
    }
}

fun shiftArrangementStartMeasure(
    changes: List<ArrangementChange>,
    rowIndex: Int,
    startMeasure: Int,
): List<ArrangementChange> {
    val safe = sanitizeArrangementChanges(changes)
    if (!isValidArrangementStartMeasure(safe, rowIndex, startMeasure)) return safe
    val delta = startMeasure.toLong() - safe[rowIndex].startMeasure
    return safe.mapIndexed { index, change ->
        if (index < rowIndex) change
        else change.copy(startMeasure = (change.startMeasure.toLong() + delta).toInt())
    }
}

fun removeArrangementChange(
    changes: List<ArrangementChange>,
    rowIndex: Int,
): List<ArrangementChange> {
    val safe = sanitizeArrangementChanges(changes)
    if (rowIndex !in safe.indices) return safe
    val updated = safe.toMutableList().apply { removeAt(rowIndex) }
    if (updated.isEmpty()) return emptyList()

    if (rowIndex == 0) {
        val delta = 1L - updated.first().startMeasure
        return updated.map { change ->
            change.copy(startMeasure = (change.startMeasure.toLong() + delta).toInt())
        }
    }
    return updated.mapIndexed { index, change ->
        if (index < rowIndex) change else change.copy(startMeasure = change.startMeasure - 1)
    }
}

fun resizeBeatPattern(beat: BeatPattern, divisions: Int): BeatPattern {
    val safeCount = divisions.coerceIn(MIN_CUSTOM_DIVISIONS, MAX_CUSTOM_DIVISIONS)
    val retained = beat.sanitized().cells.take(safeCount)
    return BeatPattern(retained + List(safeCount - retained.size) { CellSound.NORMAL })
}

internal class ArrangementSequencer(
    rawChanges: List<ArrangementChange>,
    initialMeasure: Int = 1,
    countInEnabled: Boolean = false,
) {
    private val changes = sanitizeArrangementChanges(rawChanges)
    private var currentMeasure = initialMeasure.coerceIn(1, changes.last().startMeasure)
    private var currentRowIndex = changes.indexOfLast { it.startMeasure <= currentMeasure }
        .coerceAtLeast(0)
    private var currentBeat = 0
    private var currentSubdivisionIndex = -1
    private var pendingNumberCues = emptyList<NumberCue>()
    private var countInActive = countInEnabled

    init {
        require(changes.isNotEmpty())
    }

    fun next(scheduledAtNanos: Long): PulseEvent {
        if (currentBeat == 0) {
            currentBeat = 1
            currentSubdivisionIndex = 0
        } else {
            val active = changes[currentRowIndex]
            val currentCells = active.beats[currentBeat - 1].cells
            if (currentSubdivisionIndex < currentCells.lastIndex) {
                currentSubdivisionIndex += 1
            } else if (currentBeat < active.meter.numerator) {
                currentBeat += 1
                currentSubdivisionIndex = 0
            } else {
                if (countInActive) {
                    countInActive = false
                } else {
                    val previousMeter = active.meter
                    currentMeasure = if (currentMeasure >= changes.last().startMeasure) {
                        1
                    } else {
                        currentMeasure + 1
                    }
                    currentRowIndex = changes.indexOfLast { it.startMeasure <= currentMeasure }
                        .coerceAtLeast(0)
                    pendingNumberCues = numberCuesForMeterChange(
                        previousMeter = previousMeter,
                        nextMeter = changes[currentRowIndex].meter,
                    )
                }
                currentBeat = 1
                currentSubdivisionIndex = 0
            }
        }

        val active = changes[currentRowIndex]
        val cells = active.beats[currentBeat - 1].cells
        val sound = cells[currentSubdivisionIndex]
        val accentLevel = when (sound) {
            CellSound.ACCENT -> AccentLevel.DOWNBEAT
            CellSound.SILENT -> AccentLevel.SILENT
            CellSound.NORMAL -> if (currentSubdivisionIndex == 0) {
                AccentLevel.BEAT
            } else {
                AccentLevel.SUBDIVISION
            }
        }
        val numberCues = if (currentBeat == 1 && currentSubdivisionIndex == 0) {
            pendingNumberCues.also { pendingNumberCues = emptyList() }
        } else {
            emptyList()
        }

        return PulseEvent(
            beat = currentBeat,
            subdivisionIndex = currentSubdivisionIndex,
            subdivision = Subdivision.QUARTER,
            stepWeights = List(cells.size) { 1 },
            timeSignature = null,
            playbackMode = PlaybackMode.CUSTOM,
            activeCustomPattern = active.beats,
            bpm = active.bpm,
            accentLevel = accentLevel,
            scheduledAtNanos = scheduledAtNanos,
            isCountIn = countInActive,
            arrangementMeter = active.meter,
            measureNumber = currentMeasure,
            arrangementRowIndex = currentRowIndex,
            numberCues = numberCues,
        )
    }

    private fun numberCuesForMeterChange(
        previousMeter: ArrangementMeter,
        nextMeter: ArrangementMeter,
    ): List<NumberCue> {
        if (nextMeter == previousMeter) return emptyList()

        return if (nextMeter.denominator != previousMeter.denominator) {
            listOf(
                NumberCue(nextMeter.numerator),
                NumberCue(nextMeter.denominator, DENOMINATOR_CUE_DELAY_MILLIS),
            )
        } else {
            listOf(NumberCue(nextMeter.numerator))
        }
    }
}
