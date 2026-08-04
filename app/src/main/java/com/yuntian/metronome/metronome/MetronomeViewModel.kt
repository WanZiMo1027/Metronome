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
    private val engine: MetronomeEngine = AndroidMetronomeEngine()

    private val initialSettings = repository.load()
    private val _uiState = MutableStateFlow(
        MetronomeUiState(
            bpm = initialSettings.bpm,
            activeBpm = initialSettings.bpm,
            step = initialSettings.step,
            activeTimeSignature = initialSettings.timeSignature,
            activeSubdivision = initialSettings.subdivision,
            accentEnabled = initialSettings.accentEnabled,
            playbackMode = initialSettings.playbackMode,
            activePlaybackMode = initialSettings.playbackMode,
            customPattern = initialSettings.customPattern,
            activeCustomPattern = initialSettings.customPattern,
            customPresets = repository.loadPresets(),
        ),
    )
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    private var playbackGeneration = 0L

    fun togglePlayback() {
        if (_uiState.value.isPlaying) stop() else start()
    }

    fun start() {
        if (_uiState.value.isPlaying) return
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
                            activeTimeSignature = event.timeSignature,
                            activeSubdivision = event.subdivision,
                            activePlaybackMode = event.playbackMode,
                            activeCustomPattern = event.activeCustomPattern,
                            pendingTimeSignature = state.pendingTimeSignature
                                ?.takeUnless { it == event.timeSignature },
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

    private fun settingsChanged(applyTempoImmediately: Boolean = true) {
        val settings = _uiState.value.playbackSettings().sanitized()
        repository.save(settings)
        engine.updateSettings(settings)
        if (applyTempoImmediately) engine.updateTempo(settings.bpm)
    }

    override fun onCleared() {
        engine.stop()
    }
}
