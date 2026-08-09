package com.yuntian.metronome.metronome

import kotlin.math.max

internal const val ARRANGEMENT_EXPORT_MAX_DURATION_SECONDS = 60 * 60L
internal const val ARRANGEMENT_EXPORT_BIT_RATE_KBPS = 192

internal class ArrangementExportTooLongException : IllegalArgumentException()

internal data class AudioClipFrameLengths(
    val downbeat: Int,
    val otherBeat: Int,
    val numbers: List<Int>,
) {
    init {
        require(downbeat >= 0)
        require(otherBeat >= 0)
        require(numbers.size == 11)
        require(numbers.all { it >= 0 })
    }
}

internal data class ArrangementExportPlan(
    val arrangementEndFrame: Long,
    val totalFrames: Long,
    val pulseCount: Int,
) {
    val durationSeconds: Double
        get() = totalFrames.toDouble() / METRONOME_PCM_SAMPLE_RATE
}

internal fun createArrangementExportPlan(
    rawChanges: List<ArrangementChange>,
    options: ArrangementExportOptions,
    clips: AudioClipFrameLengths,
    maximumFrames: Long? = null,
): ArrangementExportPlan {
    val sequencer = ArrangementExportSequencer(rawChanges, options)
    val timeline = AudioFrameTimeline()
    var event = sequencer.nextOrNull()
        ?: throw IllegalArgumentException("Arrangement must contain at least one change")
    var eventFrame = 0L
    var contentEndFrame = 0L
    var pulseCount = 0

    while (true) {
        pulseCount += 1
        contentEndFrame = max(contentEndFrame, eventAudioEndFrame(eventFrame, event, clips))
        eventFrame = timeline.advance(
            bpm = event.bpm,
            stepWeights = event.stepWeights,
            stepIndex = event.subdivisionIndex,
        )
        if (
            maximumFrames != null &&
            max(eventFrame, contentEndFrame) > maximumFrames
        ) {
            throw ArrangementExportTooLongException()
        }
        event = sequencer.nextOrNull() ?: break
    }

    return ArrangementExportPlan(
        arrangementEndFrame = timeline.currentFrame,
        totalFrames = max(timeline.currentFrame, contentEndFrame),
        pulseCount = pulseCount,
    )
}

internal class ArrangementExportPcmRenderer(
    rawChanges: List<ArrangementChange>,
    options: ArrangementExportOptions,
    private val clickSamples: ClickSamples,
    maximumFrames: Long? = null,
    val plan: ArrangementExportPlan = createArrangementExportPlan(
        rawChanges = rawChanges,
        options = options,
        clips = clickSamples.frameLengths(),
        maximumFrames = maximumFrames,
    ),
) {
    private val sequencer = ArrangementExportSequencer(rawChanges, options)
    private val frameTimeline = AudioFrameTimeline()
    private val activeVoices = mutableListOf<ExportPcmVoice>()
    private var mixAccumulator = IntArray(0)
    private var outputBuffer = ShortArray(0)
    private var renderedFrames = 0L
    private var nextPulse = sequencer.nextOrNull()?.let { ScheduledPulse(0L, it) }

    val hasRemainingFrames: Boolean
        get() = renderedFrames < plan.totalFrames

    val progress: Float
        get() = if (plan.totalFrames == 0L) 1f
        else (renderedFrames.toDouble() / plan.totalFrames).toFloat().coerceIn(0f, 1f)

    fun render(frameCount: Int): ShortArray {
        require(frameCount > 0)
        if (!hasRemainingFrames) return ShortArray(0)

        val actualFrameCount = minOf(frameCount.toLong(), plan.totalFrames - renderedFrames).toInt()
        val blockStart = renderedFrames
        val blockEnd = blockStart + actualFrameCount

        while (true) {
            val scheduled = nextPulse ?: break
            if (scheduled.framePosition >= blockEnd) break
            schedulePulse(scheduled)
            val nextFrame = frameTimeline.advance(
                bpm = scheduled.event.bpm,
                stepWeights = scheduled.event.stepWeights,
                stepIndex = scheduled.event.subdivisionIndex,
            )
            nextPulse = sequencer.nextOrNull()?.let { ScheduledPulse(nextFrame, it) }
        }

        val sampleCount = actualFrameCount * METRONOME_PCM_CHANNEL_COUNT
        ensureBuffers(sampleCount)
        mixAccumulator.fill(0, 0, sampleCount)
        val iterator = activeVoices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            val voiceFrameCount = voice.samples.size / METRONOME_PCM_CHANNEL_COUNT
            val voiceEnd = voice.startFrame + voiceFrameCount
            val overlapStart = max(blockStart, voice.startFrame)
            val overlapEnd = minOf(blockEnd, voiceEnd)
            if (overlapStart < overlapEnd) {
                val overlapFrames = (overlapEnd - overlapStart).toInt()
                val sourceOffset = ((overlapStart - voice.startFrame) *
                    METRONOME_PCM_CHANNEL_COUNT).toInt()
                val destinationOffset = ((overlapStart - blockStart) *
                    METRONOME_PCM_CHANNEL_COUNT).toInt()
                mixPcm16Samples(
                    accumulator = mixAccumulator,
                    source = voice.samples,
                    sourceOffset = sourceOffset,
                    destinationOffset = destinationOffset,
                    sampleCount = overlapFrames * METRONOME_PCM_CHANNEL_COUNT,
                )
            }
            if (voiceEnd <= blockEnd) iterator.remove()
        }

        renderedFrames = blockEnd
        saturatedPcm16Into(mixAccumulator, outputBuffer)
        return if (outputBuffer.size == sampleCount) outputBuffer else outputBuffer.copyOf(sampleCount)
    }

    private fun schedulePulse(scheduled: ScheduledPulse) {
        clickSamples.forAccent(scheduled.event.accentLevel)?.let { samples ->
            activeVoices += ExportPcmVoice(scheduled.framePosition, samples)
        }
        scheduled.event.numberCues.forEach { cue ->
            activeVoices += ExportPcmVoice(
                startFrame = numberCueStartFrame(scheduled.framePosition, cue.delayMillis),
                samples = clickSamples.numbers[cue.value - 1],
            )
        }
    }

    private fun ensureBuffers(sampleCount: Int) {
        if (mixAccumulator.size != sampleCount) {
            mixAccumulator = IntArray(sampleCount)
            outputBuffer = ShortArray(sampleCount)
        }
    }
}

private class ArrangementExportSequencer(
    rawChanges: List<ArrangementChange>,
    private val options: ArrangementExportOptions,
) {
    private val changes = sanitizeArrangementChanges(rawChanges)
    private val sequencer = changes.takeIf { it.isNotEmpty() }?.let {
        ArrangementSequencer(
            rawChanges = it,
            initialMeasure = 1,
            countInEnabled = options.includeCountIn,
        )
    }
    private var playbackStarted = false
    private var finished = false

    fun nextOrNull(): PulseEvent? {
        if (finished) return null
        val event = sequencer?.next(0L) ?: return null
        val isPlaybackFirstPulse = !event.isCountIn &&
            event.measureNumber == 1 &&
            event.beat == 1 &&
            event.subdivisionIndex == 0
        if (isPlaybackFirstPulse && playbackStarted) {
            finished = true
            return null
        }
        if (isPlaybackFirstPulse) playbackStarted = true
        return if (options.includeNumberCues) event else event.copy(numberCues = emptyList())
    }
}

private fun ClickSamples.frameLengths(): AudioClipFrameLengths = AudioClipFrameLengths(
    downbeat = downbeat.size / METRONOME_PCM_CHANNEL_COUNT,
    otherBeat = otherBeat.size / METRONOME_PCM_CHANNEL_COUNT,
    numbers = numbers.map { it.size / METRONOME_PCM_CHANNEL_COUNT },
)

private fun ClickSamples.forAccent(accentLevel: AccentLevel): ShortArray? = when (accentLevel) {
    AccentLevel.DOWNBEAT -> downbeat
    AccentLevel.BEAT, AccentLevel.SUBDIVISION -> otherBeat
    AccentLevel.SILENT -> null
}

private fun eventAudioEndFrame(
    eventFrame: Long,
    event: PulseEvent,
    clips: AudioClipFrameLengths,
): Long {
    var endFrame = eventFrame + when (event.accentLevel) {
        AccentLevel.DOWNBEAT -> clips.downbeat
        AccentLevel.BEAT, AccentLevel.SUBDIVISION -> clips.otherBeat
        AccentLevel.SILENT -> 0
    }
    event.numberCues.forEach { cue ->
        endFrame = max(
            endFrame,
            numberCueStartFrame(eventFrame, cue.delayMillis) + clips.numbers[cue.value - 1],
        )
    }
    return endFrame
}

private data class ExportPcmVoice(
    val startFrame: Long,
    val samples: ShortArray,
)
