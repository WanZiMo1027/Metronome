package com.yuntian.metronome.metronome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MetronomeSettingsRepository =
        SharedPreferencesMetronomeSettingsRepository(application)
    private val engine: MetronomeEngine = AndroidMetronomeEngine(application.applicationContext)

    private val initialSettings = repository.load()
    private val initialArrangementDraft = repository.loadArrangementDraft()
    private val _uiState = MutableStateFlow(
        MetronomeUiState(
            bpm = initialSettings.bpm,
            activeBpm = initialSettings.bpm,
            step = initialSettings.step,
            activeTimeSignature = initialSettings.timeSignature,
            activeSubdivision = initialSettings.subdivision,
            accentEnabled = initialSettings.accentEnabled,
            countInEnabled = initialSettings.countInEnabled,
            playbackMode = initialSettings.playbackMode,
            activePlaybackMode = initialSettings.playbackMode,
            customPattern = initialSettings.customPattern,
            activeCustomPattern = initialSettings.customPattern,
            customPresets = repository.loadPresets(),
        ),
    )
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    private val _arrangementUiState = MutableStateFlow(
        ArrangementUiState(
            changes = initialArrangementDraft,
            presets = repository.loadArrangementPresets(),
            selectedRowIndex = initialArrangementDraft.lastIndex.takeIf { it >= 0 },
            countInEnabled = initialSettings.countInEnabled,
        ),
    )
    val arrangementUiState: StateFlow<ArrangementUiState> = _arrangementUiState.asStateFlow()

    private var playbackGeneration = 0L

    fun togglePlayback() {
        if (_uiState.value.isPlaying) stop() else start()
    }

    fun start() {
        if (_uiState.value.isPlaying || _arrangementUiState.value.isPlaying) return
        val generation = ++playbackGeneration
        val started = engine.start(
            settings = _uiState.value.playbackSettings(),
            onPulse = { event ->
                viewModelScope.launch {
                    if (generation != playbackGeneration || !_uiState.value.isPlaying) {
                        return@launch
                    }
                    _uiState.update { state ->
                        state.copy(
                            activeBpm = event.bpm,
                            currentBeat = event.beat,
                            currentSubdivisionIndex = event.subdivisionIndex,
                            currentSubdivisionCount = event.subdivisionCount,
                            activeTimeSignature = event.timeSignature ?: state.activeTimeSignature,
                            activeSubdivision = event.subdivision,
                            activePlaybackMode = event.playbackMode,
                            activeCustomPattern = event.activeCustomPattern,
                            isCountIn = event.isCountIn,
                            pendingTimeSignature = event.timeSignature?.let { signature ->
                                state.pendingTimeSignature?.takeUnless { it == signature }
                            } ?: state.pendingTimeSignature,
                            pendingSubdivision = state.pendingSubdivision
                                ?.takeUnless { it == event.subdivision },
                        )
                    }
                }
            },
            onError = {
                viewModelScope.launch {
                    if (generation != playbackGeneration) return@launch
                    playbackGeneration += 1
                    _uiState.update { state ->
                        state.copy(
                            isPlaying = false,
                            isCountIn = false,
                            currentBeat = null,
                            currentSubdivisionIndex = null,
                            currentSubdivisionCount = null,
                            errorMessage = "音频初始化失败，请重试",
                        )
                    }
                }
            },
        )
        if (started) {
            _uiState.update {
                it.copy(
                    isPlaying = true,
                    isCountIn = it.countInEnabled,
                    currentBeat = null,
                    currentSubdivisionIndex = null,
                    currentSubdivisionCount = null,
                    errorMessage = null,
                )
            }
        }
    }

    fun stop() {
        playbackGeneration += 1
        engine.stop()
        _uiState.update { state ->
            val selectedSignature = state.selectedTimeSignature
            val selectedPattern = sanitizeCustomPattern(state.customPattern, selectedSignature)
            state.copy(
                isPlaying = false,
                isCountIn = false,
                activeBpm = state.bpm,
                currentBeat = null,
                currentSubdivisionIndex = null,
                currentSubdivisionCount = null,
                activeTimeSignature = selectedSignature,
                pendingTimeSignature = null,
                activeSubdivision = state.selectedSubdivision,
                pendingSubdivision = null,
                activePlaybackMode = state.playbackMode,
                customPattern = selectedPattern,
                activeCustomPattern = selectedPattern,
            )
        }
        _arrangementUiState.update {
            it.copy(
                isPlaying = false,
                isCountIn = false,
                currentMeasure = null,
                currentRowIndex = null,
                currentBeat = null,
                currentSubdivisionIndex = null,
                currentSubdivisionCount = null,
            )
        }
    }

    fun setBpm(bpm: Int) {
        val safeBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
        _uiState.update { it.copy(bpm = safeBpm, activeBpm = safeBpm) }
        settingsChanged(applyTempoImmediately = true)
    }

    fun adjustBpm(direction: Int) {
        val state = _uiState.value
        setBpm(state.bpm + direction.coerceIn(-1, 1))
    }

    fun setStep(step: Int) {
        _uiState.update { it.copy(step = if (step == 5) 5 else 1) }
        settingsChanged()
    }

    fun setTimeSignature(timeSignature: TimeSignature) {
        _uiState.update { state ->
            val resizedPattern = sanitizeCustomPattern(state.customPattern, timeSignature)
            if (state.isPlaying) {
                state.copy(
                    pendingTimeSignature = timeSignature.takeUnless {
                        it == state.activeTimeSignature
                    },
                    customPattern = resizedPattern,
                )
            } else {
                state.copy(
                    activeTimeSignature = timeSignature,
                    pendingTimeSignature = null,
                    customPattern = resizedPattern,
                    activeCustomPattern = resizedPattern,
                    currentBeat = null,
                    currentSubdivisionIndex = null,
                    currentSubdivisionCount = null,
                )
            }
        }
        settingsChanged()
    }

    fun setSubdivision(subdivision: Subdivision) {
        _uiState.update { state ->
            if (state.isPlaying) {
                state.copy(
                    pendingSubdivision = subdivision.takeUnless {
                        it == state.activeSubdivision
                    },
                )
            } else {
                state.copy(
                    activeSubdivision = subdivision,
                    pendingSubdivision = null,
                    currentSubdivisionIndex = null,
                    currentSubdivisionCount = null,
                )
            }
        }
        settingsChanged()
    }

    fun setAccentEnabled(enabled: Boolean) {
        _uiState.update { it.copy(accentEnabled = enabled) }
        settingsChanged()
    }

    fun setCountInEnabled(enabled: Boolean) {
        if (_uiState.value.isPlaying || _arrangementUiState.value.isPlaying) return
        _uiState.update { it.copy(countInEnabled = enabled) }
        _arrangementUiState.update { it.copy(countInEnabled = enabled) }
        settingsChanged()
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _uiState.update { state ->
            if (state.isPlaying) {
                state.copy(playbackMode = mode)
            } else {
                state.copy(playbackMode = mode, activePlaybackMode = mode)
            }
        }
        settingsChanged()
    }

    fun setCustomBeatDivisions(beatIndex: Int, divisions: Int) {
        if (divisions !in MIN_CUSTOM_DIVISIONS..MAX_CUSTOM_DIVISIONS) return
        updateCustomPattern { pattern ->
            if (beatIndex !in pattern.indices) pattern else pattern.toMutableList().apply {
                this[beatIndex] = BeatPattern.normal(divisions)
            }
        }
    }

    fun cycleCustomCell(beatIndex: Int, cellIndex: Int) {
        updateCustomPattern { pattern ->
            if (beatIndex !in pattern.indices) return@updateCustomPattern pattern
            val beat = pattern[beatIndex]
            if (cellIndex !in beat.cells.indices) return@updateCustomPattern pattern
            pattern.toMutableList().apply {
                this[beatIndex] = beat.copy(
                    cells = beat.cells.toMutableList().apply {
                        this[cellIndex] = this[cellIndex].next()
                    },
                )
            }
        }
    }

    fun saveCustomPreset(name: String): Boolean {
        val safeName = name.trim()
        if (safeName.isEmpty()) return false
        val state = _uiState.value
        val existing = state.customPresets.firstOrNull {
            it.name.equals(safeName, ignoreCase = true)
        }
        val preset = CustomPreset(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = safeName,
            bpm = state.bpm,
            timeSignature = state.selectedTimeSignature,
            beats = sanitizeCustomPattern(state.customPattern, state.selectedTimeSignature),
        ).sanitized()
        val updated = if (existing == null) {
            state.customPresets + preset
        } else {
            state.customPresets.map { if (it.id == existing.id) preset else it }
        }
        _uiState.update { it.copy(customPresets = updated) }
        repository.savePresets(updated)
        return true
    }

    fun deleteCustomPreset(id: String) {
        val updated = _uiState.value.customPresets.filterNot { it.id == id }
        _uiState.update { it.copy(customPresets = updated) }
        repository.savePresets(updated)
    }

    fun applyCustomPreset(id: String) {
        val preset = _uiState.value.customPresets.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            val beats = sanitizeCustomPattern(preset.beats, preset.timeSignature)
            if (state.isPlaying) {
                state.copy(
                    bpm = preset.bpm,
                    pendingTimeSignature = preset.timeSignature.takeUnless {
                        it == state.activeTimeSignature
                    },
                    playbackMode = PlaybackMode.CUSTOM,
                    customPattern = beats,
                )
            } else {
                state.copy(
                    bpm = preset.bpm,
                    activeBpm = preset.bpm,
                    activeTimeSignature = preset.timeSignature,
                    pendingTimeSignature = null,
                    playbackMode = PlaybackMode.CUSTOM,
                    activePlaybackMode = PlaybackMode.CUSTOM,
                    customPattern = beats,
                    activeCustomPattern = beats,
                    currentBeat = null,
                    currentSubdivisionIndex = null,
                    currentSubdivisionCount = null,
                )
            }
        }
        settingsChanged(applyTempoImmediately = !_uiState.value.isPlaying)
    }

    fun toggleArrangementPlayback() {
        if (_arrangementUiState.value.isPlaying) {
            stop()
        } else {
            startArrangement(_arrangementUiState.value.playbackStartMeasure)
        }
    }

    fun playArrangementFromMeasure(startMeasure: Int) {
        val changes = sanitizeArrangementChanges(_arrangementUiState.value.changes)
        if (changes.isEmpty() || _uiState.value.isPlaying) return
        val safeStartMeasure = startMeasure.coerceIn(1, changes.last().startMeasure)
        // A measure tap is an explicit request for a fresh session. Stopping unconditionally
        // also clears any engine session whose callback state has already been invalidated.
        stop()
        _arrangementUiState.update {
            it.copy(playbackStartMeasure = safeStartMeasure)
        }
        startArrangement(safeStartMeasure)
    }

    fun startArrangement(startMeasure: Int = _arrangementUiState.value.playbackStartMeasure) {
        val changes = sanitizeArrangementChanges(_arrangementUiState.value.changes)
        if (changes.isEmpty() || _uiState.value.isPlaying || _arrangementUiState.value.isPlaying) {
            return
        }
        val safeStartMeasure = startMeasure.coerceIn(1, changes.last().startMeasure)
        val generation = ++playbackGeneration
        val started = engine.startArrangement(
            changes = changes,
            startMeasure = safeStartMeasure,
            countInEnabled = _arrangementUiState.value.countInEnabled,
            onPulse = { event ->
                viewModelScope.launch {
                    if (generation != playbackGeneration || !_arrangementUiState.value.isPlaying) {
                        return@launch
                    }
                    _arrangementUiState.update { state ->
                        state.copy(
                            currentMeasure = event.measureNumber,
                            currentRowIndex = event.arrangementRowIndex,
                            currentBeat = event.beat,
                            currentSubdivisionIndex = event.subdivisionIndex,
                            currentSubdivisionCount = event.subdivisionCount,
                            isCountIn = event.isCountIn,
                        )
                    }
                }
            },
            onError = {
                viewModelScope.launch {
                    if (generation != playbackGeneration) return@launch
                    playbackGeneration += 1
                    _arrangementUiState.update { state ->
                        state.copy(
                            isPlaying = false,
                            isCountIn = false,
                            currentMeasure = null,
                            currentRowIndex = null,
                            currentBeat = null,
                            currentSubdivisionIndex = null,
                            currentSubdivisionCount = null,
                            errorMessage = "音频初始化失败，请重试",
                        )
                    }
                }
            },
        )
        if (started) {
            _arrangementUiState.update {
                it.copy(
                    changes = changes,
                    playbackStartMeasure = safeStartMeasure,
                    isPlaying = true,
                    isCountIn = it.countInEnabled,
                    currentMeasure = null,
                    currentRowIndex = null,
                    currentBeat = null,
                    currentSubdivisionIndex = null,
                    currentSubdivisionCount = null,
                    errorMessage = null,
                )
            }
        }
    }

    fun selectArrangementChange(rowIndex: Int) {
        _arrangementUiState.update { state ->
            if (state.isPlaying || rowIndex !in state.changes.indices) state
            else state.copy(selectedRowIndex = rowIndex)
        }
    }

    fun addArrangementChange() {
        val state = _arrangementUiState.value
        if (state.isPlaying) return
        if (state.changes.isEmpty()) {
            persistArrangementDraft(listOf(ArrangementChange()), selectedRowIndex = 0)
            return
        }
        val selected = state.selectedRowIndex?.takeIf { it in state.changes.indices }
            ?: state.changes.lastIndex
        val updated = insertArrangementChangeAfter(state.changes, selected)
        val inserted = updated.size > state.changes.size
        persistArrangementDraft(
            updated,
            selectedRowIndex = if (inserted) selected + 1 else selected,
        )
    }

    fun deleteArrangementChange(rowIndex: Int) {
        val state = _arrangementUiState.value
        if (!canEditArrangementRow(state, rowIndex)) return
        val updated = removeArrangementChange(state.changes, rowIndex)
        val nextSelection = when {
            updated.isEmpty() -> null
            rowIndex == 0 -> 0
            else -> (rowIndex - 1).coerceAtMost(updated.lastIndex)
        }
        persistArrangementDraft(updated, nextSelection)
    }

    fun setArrangementStartMeasure(rowIndex: Int, startMeasure: Int): Boolean {
        val changes = _arrangementUiState.value.changes
        if (!canEditArrangementRow(_arrangementUiState.value, rowIndex) || rowIndex == 0) {
            return false
        }
        if (!isValidArrangementStartMeasure(changes, rowIndex, startMeasure)) return false
        updateArrangementDraft { current ->
            shiftArrangementStartMeasure(current, rowIndex, startMeasure)
        }
        return true
    }

    fun setArrangementConfiguration(
        rowIndex: Int,
        bpm: Int,
        meter: ArrangementMeter,
    ) {
        if (!canEditArrangementRow(_arrangementUiState.value, rowIndex)) return
        updateArrangementDraft { changes ->
            if (rowIndex !in changes.indices) return@updateArrangementDraft changes
            val safeMeter = meter.sanitized()
            changes.toMutableList().apply {
                val current = this[rowIndex]
                this[rowIndex] = current.copy(
                    bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
                    meter = safeMeter,
                    beats = sanitizeArrangementPattern(current.beats, safeMeter),
                )
            }
        }
    }

    fun setArrangementBeatDivisions(rowIndex: Int, beatIndex: Int, divisions: Int) {
        if (divisions !in MIN_CUSTOM_DIVISIONS..MAX_CUSTOM_DIVISIONS) return
        if (!canEditArrangementRow(_arrangementUiState.value, rowIndex)) return
        updateArrangementDraft { changes ->
            if (rowIndex !in changes.indices) return@updateArrangementDraft changes
            val row = changes[rowIndex]
            if (beatIndex !in row.beats.indices) return@updateArrangementDraft changes
            changes.toMutableList().apply {
                val updatedBeats = row.beats.toMutableList().apply {
                    this[beatIndex] = resizeBeatPattern(this[beatIndex], divisions)
                }
                this[rowIndex] = row.copy(beats = updatedBeats)
            }
        }
    }

    fun cycleArrangementCell(rowIndex: Int, beatIndex: Int, cellIndex: Int) {
        if (!canEditArrangementRow(_arrangementUiState.value, rowIndex)) return
        updateArrangementDraft { changes ->
            if (rowIndex !in changes.indices) return@updateArrangementDraft changes
            val row = changes[rowIndex]
            if (beatIndex !in row.beats.indices) return@updateArrangementDraft changes
            val beat = row.beats[beatIndex]
            if (cellIndex !in beat.cells.indices) return@updateArrangementDraft changes
            changes.toMutableList().apply {
                val updatedBeats = row.beats.toMutableList().apply {
                    this[beatIndex] = beat.copy(
                        cells = beat.cells.toMutableList().apply {
                            this[cellIndex] = this[cellIndex].next()
                        },
                    )
                }
                this[rowIndex] = row.copy(beats = updatedBeats)
            }
        }
    }

    fun saveArrangementPreset(name: String): Boolean {
        val safeName = name.trim()
        val state = _arrangementUiState.value
        if (safeName.isEmpty() || state.changes.isEmpty() || state.isPlaying) return false
        val existing = state.presets.firstOrNull { it.name.equals(safeName, ignoreCase = true) }
        val preset = ArrangementPreset(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = safeName,
            changes = state.changes,
        ).sanitized()
        val updated = if (existing == null) {
            state.presets + preset
        } else {
            state.presets.map { if (it.id == existing.id) preset else it }
        }
        _arrangementUiState.update { it.copy(presets = updated) }
        repository.saveArrangementPresets(updated)
        return true
    }

    fun applyArrangementPreset(id: String) {
        val state = _arrangementUiState.value
        if (state.isPlaying) return
        val preset = state.presets.firstOrNull { it.id == id } ?: return
        val changes = sanitizeArrangementChanges(preset.changes)
        _arrangementUiState.update {
            it.copy(
                changes = changes,
                selectedRowIndex = changes.lastIndex.takeIf { index -> index >= 0 },
                playbackStartMeasure = 1,
            )
        }
        repository.saveArrangementDraft(changes)
    }

    fun deleteArrangementPreset(id: String) {
        if (_arrangementUiState.value.isPlaying) return
        val updated = _arrangementUiState.value.presets.filterNot { it.id == id }
        _arrangementUiState.update { it.copy(presets = updated) }
        repository.saveArrangementPresets(updated)
    }

    fun consumeArrangementError() {
        _arrangementUiState.update { it.copy(errorMessage = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun updateCustomPattern(transform: (List<BeatPattern>) -> List<BeatPattern>) {
        _uiState.update { state ->
            val safeCurrent = sanitizeCustomPattern(
                state.customPattern,
                state.selectedTimeSignature,
            )
            val updated = sanitizeCustomPattern(
                transform(safeCurrent),
                state.selectedTimeSignature,
            )
            if (state.isPlaying) {
                state.copy(customPattern = updated)
            } else {
                state.copy(customPattern = updated, activeCustomPattern = updated)
            }
        }
        settingsChanged()
    }

    private fun updateArrangementDraft(
        transform: (List<ArrangementChange>) -> List<ArrangementChange>,
    ) {
        if (_arrangementUiState.value.isPlaying) return
        persistArrangementDraft(transform(_arrangementUiState.value.changes))
    }

    private fun canEditArrangementRow(state: ArrangementUiState, rowIndex: Int): Boolean =
        !state.isPlaying && rowIndex in state.changes.indices && state.selectedRowIndex == rowIndex

    private fun persistArrangementDraft(
        changes: List<ArrangementChange>,
        selectedRowIndex: Int? = _arrangementUiState.value.selectedRowIndex,
    ) {
        val updated = sanitizeArrangementChanges(changes)
        val safeSelection = selectedRowIndex?.takeIf { it in updated.indices }
            ?: updated.lastIndex.takeIf { it >= 0 }
        _arrangementUiState.update { state ->
            val safePlaybackStart = if (updated.isEmpty()) {
                1
            } else {
                state.playbackStartMeasure.coerceIn(1, updated.last().startMeasure)
            }
            state.copy(
                changes = updated,
                selectedRowIndex = safeSelection,
                playbackStartMeasure = safePlaybackStart,
            )
        }
        repository.saveArrangementDraft(updated)
    }

    private fun settingsChanged(applyTempoImmediately: Boolean = true) {
        val settings = _uiState.value.playbackSettings().sanitized()
        repository.save(settings)
        engine.updateSettings(settings)
        if (applyTempoImmediately) engine.updateTempo(settings.bpm)
    }

    override fun onCleared() {
        engine.release()
    }
}
