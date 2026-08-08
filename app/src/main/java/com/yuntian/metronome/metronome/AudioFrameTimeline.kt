package com.yuntian.metronome.metronome

internal const val METRONOME_PCM_SAMPLE_RATE = 44_100
internal const val METRONOME_PCM_CHANNEL_COUNT = 2

internal fun numberCueStartFrame(pulseFrame: Long, delayMillis: Long): Long {
    require(pulseFrame >= 0L)
    require(delayMillis >= 0L)
    return pulseFrame + delayMillis * METRONOME_PCM_SAMPLE_RATE / 1_000L
}

/**
 * Advances an audio-frame cursor without discarding the fractional frame left by each pulse.
 * The fraction is stored as unsigned Q32 in a Long, while the whole-frame position can run for
 * the complete lifetime of the app without the overflow limit of a single Q32 frame counter.
 */
internal class AudioFrameTimeline(
    private val sampleRate: Int = METRONOME_PCM_SAMPLE_RATE,
    startFrame: Long = 0L,
) {
    private var fractionalFrameQ32 = 0L

    var currentFrame: Long = startFrame
        private set

    init {
        require(sampleRate > 0)
        require(startFrame >= 0L)
    }

    fun advance(
        bpm: Int,
        stepWeights: List<Int>,
        stepIndex: Int,
    ): Long {
        val advance = calculateAdvance(bpm, stepWeights, stepIndex)
        currentFrame += advance.wholeFrames
        fractionalFrameQ32 = advance.fractionalFrameQ32
        return currentFrame
    }

    fun previewAdvance(
        bpm: Int,
        stepWeights: List<Int>,
        stepIndex: Int,
    ): Long {
        val advance = calculateAdvance(bpm, stepWeights, stepIndex)
        return currentFrame + advance.wholeFrames
    }

    private fun calculateAdvance(
        bpm: Int,
        stepWeights: List<Int>,
        stepIndex: Int,
    ): FrameAdvance {
        require(bpm > 0)
        require(stepWeights.isNotEmpty())
        require(stepWeights.all { it > 0 })
        require(stepIndex in stepWeights.indices)

        val totalWeight = stepWeights.sumOf(Int::toLong)
        val denominator = bpm.toLong() * totalWeight
        val numerator = sampleRate.toLong() * SECONDS_PER_MINUTE * stepWeights[stepIndex]
        val wholeFrames = numerator / denominator
        val remainder = numerator % denominator
        val fractionalIncrement = (remainder shl Q32_SHIFT) / denominator
        val fractionalTotal = fractionalFrameQ32 + fractionalIncrement
        return FrameAdvance(
            wholeFrames = wholeFrames + fractionalTotal / Q32_ONE,
            fractionalFrameQ32 = fractionalTotal % Q32_ONE,
        )
    }

    private data class FrameAdvance(
        val wholeFrames: Long,
        val fractionalFrameQ32: Long,
    )

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val Q32_SHIFT = 32
        const val Q32_ONE = 1L shl Q32_SHIFT
    }
}

internal fun mixPcm16Samples(
    accumulator: IntArray,
    source: ShortArray,
    sourceOffset: Int,
    destinationOffset: Int,
    sampleCount: Int,
) {
    require(sourceOffset >= 0 && sourceOffset + sampleCount <= source.size)
    require(destinationOffset >= 0 && destinationOffset + sampleCount <= accumulator.size)
    repeat(sampleCount) { index ->
        accumulator[destinationOffset + index] += source[sourceOffset + index].toInt()
    }
}

internal fun saturatedPcm16(accumulator: IntArray): ShortArray =
    ShortArray(accumulator.size).also { output ->
        saturatedPcm16Into(accumulator, output)
    }

internal fun saturatedPcm16Into(
    accumulator: IntArray,
    output: ShortArray,
) {
    require(output.size == accumulator.size)
    accumulator.indices.forEach { index ->
        output[index] = accumulator[index]
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}
