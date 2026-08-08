package com.yuntian.metronome.metronome

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import com.yuntian.metronome.R
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

interface MetronomeEngine {
    fun start(
        settings: MetronomeSettings,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean

    fun startArrangement(
        changes: List<ArrangementChange>,
        startMeasure: Int = 1,
        countInEnabled: Boolean = false,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean

    fun updateSettings(settings: MetronomeSettings)
    fun updateTempo(bpm: Int)
    fun stop()
    fun release()
}

class AndroidMetronomeEngine(context: Context) : MetronomeEngine {
    private val applicationContext = context.applicationContext
    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MetronomePlaybackCallbacks").apply { isDaemon = true }
    }
    private val activeTrack = AtomicReference<AudioTrack?>(null)
    private val clickSamples by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ClickSamples(
            downbeat = loadRawPcm16(applicationContext, R.raw.first_beat_pcm),
            otherBeat = loadRawPcm16(applicationContext, R.raw.other_beat_pcm),
            numbers = listOf(
                loadRawPcm16(applicationContext, R.raw.click_number_001_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_002_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_003_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_004_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_005_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_006_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_007_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_008_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_009_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_010_pcm),
                loadRawPcm16(applicationContext, R.raw.click_number_011_pcm),
            ),
        )
    }

    @Volatile
    private var running = false

    @Volatile
    private var released = false

    @Volatile
    private var settings = MetronomeSettings()

    @Volatile
    private var tempoBpm = DEFAULT_BPM

    @Volatile
    private var sessionGeneration = 0L
    private var workerThread: Thread? = null

    @Synchronized
    override fun start(
        settings: MetronomeSettings,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean {
        if (released) {
            onError(IllegalStateException("Metronome engine has been released"))
            return false
        }
        if (running) return true

        this.settings = settings.sanitized()
        return startWorker(
            arrangement = null,
            arrangementStartMeasure = 1,
            countInEnabled = this.settings.countInEnabled,
            initialBpm = this.settings.bpm,
            onPulse = onPulse,
            onError = onError,
        )
    }

    @Synchronized
    override fun startArrangement(
        changes: List<ArrangementChange>,
        startMeasure: Int,
        countInEnabled: Boolean,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean {
        if (released) {
            onError(IllegalStateException("Metronome engine has been released"))
            return false
        }
        if (running) return true
        val safeChanges = sanitizeArrangementChanges(changes)
        if (safeChanges.isEmpty()) return false
        val safeStartMeasure = startMeasure.coerceIn(1, safeChanges.last().startMeasure)
        val initialChange = safeChanges.last { it.startMeasure <= safeStartMeasure }

        return startWorker(
            arrangement = safeChanges,
            arrangementStartMeasure = safeStartMeasure,
            countInEnabled = countInEnabled,
            initialBpm = initialChange.bpm,
            onPulse = onPulse,
            onError = onError,
        )
    }

    private fun startWorker(
        arrangement: List<ArrangementChange>?,
        arrangementStartMeasure: Int,
        countInEnabled: Boolean,
        initialBpm: Int,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean {
        tempoBpm = initialBpm.coerceIn(MIN_BPM, MAX_BPM)
        running = true
        sessionGeneration += 1
        val generation = sessionGeneration
        workerThread = Thread(
            {
                runScheduler(
                    generation = generation,
                    arrangement = arrangement,
                    arrangementStartMeasure = arrangementStartMeasure,
                    countInEnabled = countInEnabled,
                    onPulse = onPulse,
                    onError = onError,
                )
            },
            AUDIO_THREAD_NAME,
        ).apply { start() }
        return true
    }

    override fun updateSettings(settings: MetronomeSettings) {
        this.settings = settings.sanitized()
    }

    override fun updateTempo(bpm: Int) {
        tempoBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
    }

    override fun stop() {
        val threadToJoin: Thread?
        val trackToStop: AudioTrack?
        synchronized(this) {
            running = false
            sessionGeneration += 1
            threadToJoin = workerThread
            trackToStop = activeTrack.get()
        }
        stopTrack(trackToStop)
        if (threadToJoin != null && threadToJoin !== Thread.currentThread()) {
            threadToJoin.join(STOP_JOIN_TIMEOUT_MILLIS)
            if (threadToJoin.isAlive) {
                threadToJoin.interrupt()
                releaseTrack(trackToStop)
                threadToJoin.join(STOP_INTERRUPT_JOIN_TIMEOUT_MILLIS)
            }
        }
        synchronized(this) {
            if (workerThread === threadToJoin) workerThread = null
        }
    }

    override fun release() {
        synchronized(this) {
            if (released) return
            released = true
        }
        stop()
        callbackExecutor.shutdownNow()
    }

    private fun runScheduler(
        generation: Long,
        arrangement: List<ArrangementChange>?,
        arrangementStartMeasure: Int,
        countInEnabled: Boolean,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        var track: AudioTrack? = null
        var pendingError: Throwable? = null
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            if (!isSessionRunning(generation)) return

            val output = createAudioTrack()
            track = output.track
            if (!activeTrack.compareAndSet(null, track)) {
                error("Another AudioTrack session is still active")
            }
            if (!isSessionRunning(generation)) return

            val renderer = PcmTimelineRenderer(
                clickSamples = clickSamples,
                arrangement = arrangement,
                arrangementStartMeasure = arrangementStartMeasure,
                countInEnabled = countInEnabled,
                settingsProvider = { settings },
                tempoProvider = { tempoBpm },
                startFrame = STARTUP_PREROLL_FRAMES,
            )
            val pendingPulses = ArrayDeque<ScheduledPulse>()
            val playbackHead = PlaybackHeadTracker()
            val presentationClock = AudioPresentationClock()

            val prefill = renderer.render(output.bufferFrames, pendingPulses::addLast)
            if (!writeExactly(track, prefill, generation)) return
            track.play()
            val initialUnderrunCount = track.underrunCount

            while (isSessionRunning(generation)) {
                dispatchPresentedPulses(
                    track = track,
                    generation = generation,
                    pendingPulses = pendingPulses,
                    playbackHead = playbackHead,
                    presentationClock = presentationClock,
                    onPulse = onPulse,
                )

                val block = renderer.render(RENDER_BLOCK_FRAMES, pendingPulses::addLast)
                if (!writeExactly(track, block, generation)) break
                if (!isSessionRunning(generation)) break
                if (track.underrunCount > initialUnderrunCount) {
                    error("AudioTrack underrun detected")
                }
            }
        } catch (_: InterruptedException) {
            // stop() interrupts only after stopping the track did not unblock the worker promptly.
        } catch (error: Throwable) {
            val shouldReport = synchronized(this) {
                if (running && generation == sessionGeneration) {
                    running = false
                    true
                } else {
                    false
                }
            }
            if (shouldReport) {
                Log.e(LOG_TAG, "Metronome audio output stopped unexpectedly", error)
                pendingError = error
            }
        } finally {
            stopTrack(track)
            releaseTrack(track)
            synchronized(this) {
                if (generation == sessionGeneration) running = false
                if (workerThread === Thread.currentThread()) workerThread = null
            }
        }
        pendingError?.let { dispatchError(generation, onError, it) }
    }

    private fun dispatchPresentedPulses(
        track: AudioTrack,
        generation: Long,
        pendingPulses: ArrayDeque<ScheduledPulse>,
        playbackHead: PlaybackHeadTracker,
        presentationClock: AudioPresentationClock,
        onPulse: (PulseEvent) -> Unit,
    ) {
        val nowNanos = System.nanoTime()
        val currentFrame = playbackHead.read(track.playbackHeadPosition)
        presentationClock.update(track, currentFrame, nowNanos)

        while (pendingPulses.isNotEmpty() && pendingPulses.first().framePosition <= currentFrame) {
            val scheduled = pendingPulses.removeFirst()
            val event = scheduled.event.copy(
                scheduledAtNanos = presentationClock.presentationTimeNanos(
                    framePosition = scheduled.framePosition,
                    currentFrame = currentFrame,
                    nowNanos = nowNanos,
                ),
            )
            callbackExecutor.execute {
                if (!isSessionRunning(generation)) return@execute
                runCatching { onPulse(event) }
                    .onFailure { Log.e(LOG_TAG, "Pulse callback failed", it) }
            }
        }
    }

    private fun dispatchError(
        generation: Long,
        onError: (Throwable) -> Unit,
        error: Throwable,
    ) {
        callbackExecutor.execute {
            val shouldDeliver = synchronized(this) {
                !released && generation == sessionGeneration
            }
            if (shouldDeliver) onError(error)
        }
    }

    private fun createAudioTrack(): AudioTrackOutput {
        val minBufferBytes = AudioTrack.getMinBufferSize(
            METRONOME_PCM_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferBytes > 0) { "Invalid AudioTrack minimum buffer size: $minBufferBytes" }
        val frameSizeBytes = METRONOME_PCM_CHANNEL_COUNT * Short.SIZE_BYTES
        val requestedBufferBytes = max(
            minBufferBytes,
            RENDER_BLOCK_FRAMES * frameSizeBytes * MIN_BUFFER_BLOCK_COUNT,
        )
        val bufferFrames = (requestedBufferBytes + frameSizeBytes - 1) / frameSizeBytes
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(METRONOME_PCM_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferFrames * frameSizeBytes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = builder.build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("AudioTrack initialization failed")
        }
        return AudioTrackOutput(track = track, bufferFrames = bufferFrames)
    }

    private fun writeExactly(
        track: AudioTrack,
        samples: ShortArray,
        generation: Long,
    ): Boolean {
        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (!isSessionRunning(generation)) return false
        check(written == samples.size) {
            "AudioTrack short write: expected=${samples.size}, actual=$written"
        }
        return true
    }

    private fun isSessionRunning(generation: Long): Boolean =
        running && !released && generation == sessionGeneration

    private fun stopTrack(track: AudioTrack?) {
        if (track == null) return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
        }
        runCatching { track.flush() }
        runCatching { track.stop() }
    }

    private fun releaseTrack(track: AudioTrack?) {
        if (track != null && activeTrack.compareAndSet(track, null)) {
            track.release()
        }
    }

    private data class AudioTrackOutput(
        val track: AudioTrack,
        val bufferFrames: Int,
    )

    private companion object {
        const val LOG_TAG = "MetronomeEngine"
        const val AUDIO_THREAD_NAME = "MetronomeAudioScheduler"
        const val STOP_JOIN_TIMEOUT_MILLIS = 500L
        const val STOP_INTERRUPT_JOIN_TIMEOUT_MILLIS = 100L
        const val RENDER_BLOCK_FRAMES = 441
        const val MIN_BUFFER_BLOCK_COUNT = 4
        const val STARTUP_PREROLL_FRAMES = 1_764L
    }
}

internal class PcmTimelineRenderer(
    private val clickSamples: ClickSamples,
    arrangement: List<ArrangementChange>?,
    arrangementStartMeasure: Int,
    countInEnabled: Boolean,
    private val settingsProvider: () -> MetronomeSettings,
    private val tempoProvider: () -> Int,
    startFrame: Long,
) {
    private val arrangementSequencer = arrangement?.let {
        ArrangementSequencer(it, arrangementStartMeasure, countInEnabled)
    }
    private val metronomeSequencer = if (arrangement == null) {
        PulseSequencer(settingsProvider())
    } else {
        null
    }
    private val frameTimeline = AudioFrameTimeline(startFrame = startFrame)
    private val activeVoices = mutableListOf<PcmVoice>()
    private var mixAccumulator = IntArray(0)
    private var outputBuffer = ShortArray(0)
    private var renderedFrames = 0L
    private var initialPulse: ScheduledPulse? = ScheduledPulse(
        framePosition = startFrame,
        event = nextEvent(),
    )
    private var lastScheduledPulse: ScheduledPulse? = null

    fun render(
        frameCount: Int,
        onPulseScheduled: (ScheduledPulse) -> Unit,
    ): ShortArray {
        require(frameCount > 0)
        val blockStart = renderedFrames
        val blockEnd = blockStart + frameCount

        val first = initialPulse
        if (first != null && first.framePosition < blockEnd) {
            schedulePulse(first, onPulseScheduled)
            lastScheduledPulse = first
            initialPulse = null
        }

        while (true) {
            val previous = lastScheduledPulse ?: break
            val intervalBpm = intervalBpm(previous.event)
            val nextFrame = frameTimeline.previewAdvance(
                bpm = intervalBpm,
                stepWeights = previous.event.stepWeights,
                stepIndex = previous.event.subdivisionIndex,
            )
            if (nextFrame >= blockEnd) break

            frameTimeline.advance(
                bpm = intervalBpm,
                stepWeights = previous.event.stepWeights,
                stepIndex = previous.event.subdivisionIndex,
            )
            val scheduled = ScheduledPulse(
                framePosition = nextFrame,
                event = nextEvent(),
            )
            schedulePulse(scheduled, onPulseScheduled)
            lastScheduledPulse = scheduled
        }

        val sampleCount = frameCount * METRONOME_PCM_CHANNEL_COUNT
        ensureBuffers(sampleCount)
        mixAccumulator.fill(0)
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
        return outputBuffer
    }

    private fun ensureBuffers(sampleCount: Int) {
        if (mixAccumulator.size == sampleCount) return
        mixAccumulator = IntArray(sampleCount)
        outputBuffer = ShortArray(sampleCount)
    }

    private fun schedulePulse(
        scheduled: ScheduledPulse,
        onPulseScheduled: (ScheduledPulse) -> Unit,
    ) {
        onPulseScheduled(scheduled)
        val samples = when (scheduled.event.accentLevel) {
            AccentLevel.DOWNBEAT -> clickSamples.downbeat
            AccentLevel.BEAT, AccentLevel.SUBDIVISION -> clickSamples.otherBeat
            AccentLevel.SILENT -> null
        }
        if (samples != null) {
            activeVoices += PcmVoice(
                startFrame = scheduled.framePosition,
                samples = samples,
            )
        }
        scheduled.event.numberCues.forEach { cue ->
            activeVoices += PcmVoice(
                startFrame = numberCueStartFrame(scheduled.framePosition, cue.delayMillis),
                samples = clickSamples.numbers[cue.value - 1],
            )
        }
    }

    private fun intervalBpm(event: PulseEvent): Int = if (arrangementSequencer != null) {
        event.bpm
    } else {
        tempoProvider().coerceIn(MIN_BPM, MAX_BPM)
    }

    private fun nextEvent(): PulseEvent = arrangementSequencer?.next(0L)
        ?: metronomeSequencer!!.next(
            requestedSettings = settingsProvider(),
            scheduledAtNanos = 0L,
        )
}

private class PlaybackHeadTracker {
    private var previousRawFrame: Long? = null
    private var wrapOffset = 0L

    fun read(playbackHeadPosition: Int): Long {
        val rawFrame = playbackHeadPosition.toLong() and UINT32_MASK
        val previous = previousRawFrame
        if (previous != null && previous - rawFrame > UINT32_HALF_RANGE) {
            wrapOffset += UINT32_RANGE
        }
        previousRawFrame = rawFrame
        return wrapOffset + rawFrame
    }

    private companion object {
        const val UINT32_MASK = 0xffff_ffffL
        const val UINT32_RANGE = 0x1_0000_0000L
        const val UINT32_HALF_RANGE = 0x8000_0000L
    }
}

private class AudioPresentationClock {
    private val timestamp = AudioTimestamp()
    private var mappedFrame: Long? = null
    private var mappedNanos = 0L
    private var nextTimestampQueryNanos = 0L

    fun update(track: AudioTrack, currentFrame: Long, nowNanos: Long) {
        if (nowNanos < nextTimestampQueryNanos) return
        if (track.getTimestamp(timestamp)) {
            mappedFrame = extendTimestampFrame(timestamp.framePosition, currentFrame)
            mappedNanos = timestamp.nanoTime
            nextTimestampQueryNanos = nowNanos + STABLE_TIMESTAMP_QUERY_NANOS
        } else {
            mappedFrame = null
            nextTimestampQueryNanos = nowNanos + WARMUP_TIMESTAMP_QUERY_NANOS
        }
    }

    fun presentationTimeNanos(
        framePosition: Long,
        currentFrame: Long,
        nowNanos: Long,
    ): Long {
        val anchorFrame = mappedFrame
        return if (anchorFrame != null) {
            mappedNanos + framesToNanos(framePosition - anchorFrame)
        } else {
            nowNanos - framesToNanos(currentFrame - framePosition)
        }
    }

    private fun extendTimestampFrame(rawTimestampFrame: Long, currentFrame: Long): Long {
        val rawFrame = rawTimestampFrame and UINT32_MASK
        var candidate = (currentFrame and UINT32_HIGH_MASK) + rawFrame
        if (candidate - currentFrame > UINT32_HALF_RANGE) candidate -= UINT32_RANGE
        if (currentFrame - candidate > UINT32_HALF_RANGE) candidate += UINT32_RANGE
        return candidate
    }

    private fun framesToNanos(frames: Long): Long =
        frames / METRONOME_PCM_SAMPLE_RATE * NANOS_PER_SECOND +
            frames % METRONOME_PCM_SAMPLE_RATE * NANOS_PER_SECOND /
            METRONOME_PCM_SAMPLE_RATE

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val WARMUP_TIMESTAMP_QUERY_NANOS = 100_000_000L
        const val STABLE_TIMESTAMP_QUERY_NANOS = 10_000_000_000L
        const val UINT32_MASK = 0xffff_ffffL
        const val UINT32_HIGH_MASK = -0x1_0000_0000L
        const val UINT32_RANGE = 0x1_0000_0000L
        const val UINT32_HALF_RANGE = 0x8000_0000L
    }
}

private fun loadRawPcm16(context: Context, resourceId: Int): ShortArray {
    val bytes = context.resources.openRawResource(resourceId).use { it.readBytes() }
    require(bytes.size % Short.SIZE_BYTES == 0) { "PCM resource must contain 16-bit samples" }
    return ShortArray(bytes.size / Short.SIZE_BYTES) { index ->
        val byteIndex = index * Short.SIZE_BYTES
        ((bytes[byteIndex].toInt() and 0xff) or (bytes[byteIndex + 1].toInt() shl 8)).toShort()
    }
}

internal data class ClickSamples(
    val downbeat: ShortArray,
    val otherBeat: ShortArray,
    val numbers: List<ShortArray>,
) {
    init {
        require(numbers.size == 11)
    }
}

internal data class ScheduledPulse(
    val framePosition: Long,
    val event: PulseEvent,
)

private data class PcmVoice(
    val startFrame: Long,
    val samples: ShortArray,
)
