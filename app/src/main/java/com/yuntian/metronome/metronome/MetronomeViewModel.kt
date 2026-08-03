package com.yuntian.metronome.metronome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
            step = initialSettings.step,
            activeTimeSignature = initialSettings.timeSignature,
            accentEnabled = initialSettings.accentEnabled,
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
            onBeat = { event ->
                viewModelScope.launch {
                    if (generation != playbackGeneration || !_uiState.value.isPlaying) return@launch
                    _uiState.update { state ->
                        state.copy(
                            currentBeat = event.beat,
                            activeTimeSignature = event.timeSignature,
                            pendingTimeSignature = state.pendingTimeSignature
                                ?.takeUnless { it == event.timeSignature },
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
                            errorMessage = "音频初始化失败，请重试",
                        )
                    }
                }
            },
        )
        if (started) {
            _uiState.update { it.copy(isPlaying = true, currentBeat = null, errorMessage = null) }
        }
    }

    fun stop() {
        playbackGeneration += 1
        engine.stop()
        _uiState.update { state ->
            state.copy(
                isPlaying = false,
                currentBeat = null,
                activeTimeSignature = state.selectedTimeSignature,
                pendingTimeSignature = null,
            )
        }
    }

    fun setBpm(bpm: Int) {
        _uiState.update { it.copy(bpm = bpm.coerceIn(MIN_BPM, MAX_BPM)) }
        settingsChanged()
    }

    fun adjustBpm(direction: Int) {
        val state = _uiState.value
        setBpm(state.bpm + direction * state.step)
    }

    fun setStep(step: Int) {
        _uiState.update { it.copy(step = if (step == 5) 5 else 1) }
        settingsChanged()
    }

    fun setTimeSignature(timeSignature: TimeSignature) {
        _uiState.update { state ->
            if (state.isPlaying) {
                state.copy(
                    pendingTimeSignature = timeSignature.takeUnless {
                        it == state.activeTimeSignature
                    },
                )
            } else {
                state.copy(
                    activeTimeSignature = timeSignature,
                    pendingTimeSignature = null,
                    currentBeat = null,
                )
            }
        }
        settingsChanged()
    }

    fun setAccentEnabled(enabled: Boolean) {
        _uiState.update { it.copy(accentEnabled = enabled) }
        settingsChanged()
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun settingsChanged() {
        val settings = _uiState.value.playbackSettings().sanitized()
        repository.save(settings)
        engine.updateSettings(settings)
    }

    override fun onCleared() {
        engine.stop()
    }
}
