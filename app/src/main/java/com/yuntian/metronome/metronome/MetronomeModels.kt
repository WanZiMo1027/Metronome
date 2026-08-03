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

data class MetronomeSettings(
    val bpm: Int = DEFAULT_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val step: Int = 1,
    val accentEnabled: Boolean = true,
) {
    fun sanitized(): MetronomeSettings = copy(
        bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
        step = if (step == 5) 5 else 1,
    )
}

data class BeatEvent(
    val beat: Int,
    val timeSignature: TimeSignature,
    val isAccent: Boolean,
    val scheduledAtNanos: Long,
)

data class MetronomeUiState(
    val bpm: Int = DEFAULT_BPM,
    val step: Int = 1,
    val activeTimeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val pendingTimeSignature: TimeSignature? = null,
    val accentEnabled: Boolean = true,
    val isPlaying: Boolean = false,
    val currentBeat: Int? = null,
    val errorMessage: String? = null,
) {
    val selectedTimeSignature: TimeSignature
        get() = pendingTimeSignature ?: activeTimeSignature

    fun playbackSettings(): MetronomeSettings = MetronomeSettings(
        bpm = bpm,
        timeSignature = selectedTimeSignature,
        step = step,
        accentEnabled = accentEnabled,
    )
}

fun parseBpmInput(value: String): Int? =
    value.trim().toIntOrNull()?.takeIf { it in MIN_BPM..MAX_BPM }

internal object MetronomeTiming {
    fun intervalNanos(bpm: Int): Long = 60_000_000_000L / bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun nextDeadlineNanos(lastScheduledAtNanos: Long, bpm: Int): Long =
        lastScheduledAtNanos + intervalNanos(bpm)

    fun resolveScheduledAtNanos(lastScheduledAtNanos: Long, nowNanos: Long, bpm: Int): Long {
        val interval = intervalNanos(bpm)
        val requestedDeadline = lastScheduledAtNanos + interval
        return if (nowNanos - requestedDeadline > interval) nowNanos else requestedDeadline
    }
}

internal class BeatSequencer(initialSignature: TimeSignature) {
    var activeTimeSignature: TimeSignature = initialSignature
        private set

    private var currentBeat = 0

    fun next(
        requestedSignature: TimeSignature,
        accentEnabled: Boolean,
        scheduledAtNanos: Long,
    ): BeatEvent {
        if (currentBeat == 0) {
            currentBeat = 1
        } else if (currentBeat >= activeTimeSignature.beatsPerMeasure) {
            activeTimeSignature = requestedSignature
            currentBeat = 1
        } else {
            currentBeat += 1
        }

        return BeatEvent(
            beat = currentBeat,
            timeSignature = activeTimeSignature,
            isAccent = accentEnabled && currentBeat == 1,
            scheduledAtNanos = scheduledAtNanos,
        )
    }
}
