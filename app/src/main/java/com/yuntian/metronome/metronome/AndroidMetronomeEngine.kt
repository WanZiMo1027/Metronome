package com.yuntian.metronome.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.Closeable
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

interface MetronomeEngine {
    fun start(
        settings: MetronomeSettings,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean

    fun updateSettings(settings: MetronomeSettings)
    fun stop()
}

class AndroidMetronomeEngine : MetronomeEngine {
    private val wakeSignal = Object()

    @Volatile
    private var running = false

    @Volatile
    private var settings = MetronomeSettings()

    private var workerThread: Thread? = null

    @Synchronized
    override fun start(
        settings: MetronomeSettings,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ): Boolean {
        if (running) return true

        val clickOutput = try {
            AudioTrackClickOutput()
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "Unable to initialize metronome audio", error)
            onError(error)
            return false
        }

        this.settings = settings.sanitized()
        running = true
        workerThread = Thread(
            {
                runScheduler(clickOutput, onPulse, onError)
            },
            "MetronomeAudioScheduler",
        ).apply { start() }
        return true
    }

    override fun updateSettings(settings: MetronomeSettings) {
        this.settings = settings.sanitized()
        synchronized(wakeSignal) {
            wakeSignal.notifyAll()
        }
    }

    override fun stop() {
        val threadToJoin: Thread?
        synchronized(this) {
            if (!running && workerThread == null) return
            running = false
            threadToJoin = workerThread
        }
        synchronized(wakeSignal) {
            wakeSignal.notifyAll()
        }
        if (threadToJoin != null && threadToJoin !== Thread.currentThread()) {
            threadToJoin.join(500)
            if (threadToJoin.isAlive) threadToJoin.interrupt()
        }
        synchronized(this) {
            if (workerThread === threadToJoin) workerThread = null
        }
    }

    private fun runScheduler(
        clickOutput: AudioTrackClickOutput,
        onPulse: (PulseEvent) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val sequencer = PulseSequencer(
                initialSignature = settings.timeSignature,
                initialSubdivision = settings.subdivision,
            )
            var lastScheduledAt = System.nanoTime()
            var currentEvent = emitPulse(sequencer, lastScheduledAt, clickOutput, onPulse)

            while (running) {
                val snapshot = settings
                val requestedDeadline = MetronomeTiming.nextDeadlineNanos(
                    lastScheduledAtNanos = lastScheduledAt,
                    bpm = snapshot.bpm,
                    subdivision = currentEvent.subdivision,
                    stepIndex = currentEvent.subdivisionIndex,
                )
                if (!waitUntil(requestedDeadline)) continue
                if (!running) break

                val latest = settings
                val latestDeadline = MetronomeTiming.nextDeadlineNanos(
                    lastScheduledAtNanos = lastScheduledAt,
                    bpm = latest.bpm,
                    subdivision = currentEvent.subdivision,
                    stepIndex = currentEvent.subdivisionIndex,
                )
                val now = System.nanoTime()

                // A slower tempo selected while waiting moves the next beat later.
                if (latestDeadline > now) continue

                // Never fire a burst of catch-up clicks after a long scheduler stall.
                val scheduledAt = MetronomeTiming.resolveScheduledAtNanos(
                    lastScheduledAtNanos = lastScheduledAt,
                    nowNanos = now,
                    bpm = latest.bpm,
                    subdivision = currentEvent.subdivision,
                    stepIndex = currentEvent.subdivisionIndex,
                )
                currentEvent = emitPulse(sequencer, scheduledAt, clickOutput, onPulse)
                lastScheduledAt = scheduledAt
            }
        } catch (_: InterruptedException) {
            // stop() interrupts only as a final wake-up fallback.
        } catch (error: Throwable) {
            if (running) {
                Log.e(LOG_TAG, "Metronome scheduler stopped unexpectedly", error)
                onError(error)
            }
        } finally {
            running = false
            clickOutput.close()
            synchronized(this) {
                if (workerThread === Thread.currentThread()) workerThread = null
            }
        }
    }

    private fun emitPulse(
        sequencer: PulseSequencer,
        scheduledAtNanos: Long,
        clickOutput: AudioTrackClickOutput,
        onPulse: (PulseEvent) -> Unit,
    ): PulseEvent {
        val latest = settings
        val event = sequencer.next(
            requestedSignature = latest.timeSignature,
            requestedSubdivision = latest.subdivision,
            accentEnabled = latest.accentEnabled,
            scheduledAtNanos = scheduledAtNanos,
        )
        if (running) {
            clickOutput.play(event.accentLevel)
            onPulse(event)
        }
        return event
    }

    private fun waitUntil(deadlineNanos: Long): Boolean {
        synchronized(wakeSignal) {
            if (!running) return false
            val remaining = deadlineNanos - System.nanoTime()
            if (remaining <= 0L) return true
            val millis = remaining / 1_000_000L
            val nanos = (remaining % 1_000_000L).toInt()
            wakeSignal.wait(millis, nanos)
            return deadlineNanos <= System.nanoTime()
        }
    }

    private companion object {
        const val LOG_TAG = "MetronomeEngine"
    }
}

private class AudioTrackClickOutput : Closeable {
    private val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        .coerceAtLeast(22_050)
    private val downbeatTrack = buildTrack(createDownbeatClick())
    private val beatTrack = try {
        buildTrack(createClick(frequency = 780.0, durationMs = 30, strength = 0.70))
    } catch (error: Throwable) {
        downbeatTrack.track.release()
        throw error
    }
    private val subdivisionTrack = try {
        buildTrack(createClick(frequency = 1_080.0, durationMs = 18, strength = 0.48))
    } catch (error: Throwable) {
        downbeatTrack.track.release()
        beatTrack.track.release()
        throw error
    }

    fun play(accentLevel: AccentLevel) {
        val click = when (accentLevel) {
            AccentLevel.DOWNBEAT -> downbeatTrack
            AccentLevel.BEAT -> beatTrack
            AccentLevel.SUBDIVISION -> subdivisionTrack
        }
        val track = click.track
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
        track.flush()
        val written = track.write(click.samples, 0, click.samples.size, AudioTrack.WRITE_BLOCKING)
        if (written != click.samples.size) error("Could not queue metronome sound")
        track.play()
    }

    override fun close() {
        listOf(downbeatTrack.track, beatTrack.track, subdivisionTrack.track).forEach { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
            track.release()
        }
    }

    private fun buildTrack(samples: ShortArray): ClickTrack {
        val minimumBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(0)
        val bufferSize = max(samples.size * Short.SIZE_BYTES, minimumBufferSize)
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
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = builder.build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("AudioTrack initialization failed (rate=$sampleRate, buffer=$bufferSize)")
        }
        return ClickTrack(track = track, samples = samples)
    }

    private fun createClick(frequency: Double, durationMs: Int, strength: Double): ShortArray {
        val sampleCount = (sampleRate * durationMs / 1_000.0).roundToInt()
        return ShortArray(sampleCount) { index ->
            val seconds = index.toDouble() / sampleRate
            val progress = index.toDouble() / sampleCount
            val envelope = exp(-7.0 * progress) * (1.0 - exp(-80.0 * progress))
            val fundamental = sin(2.0 * PI * frequency * seconds)
            val harmonic = 0.22 * sin(2.0 * PI * frequency * 2.0 * seconds)
            ((fundamental + harmonic) * envelope * strength * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun createDownbeatClick(): ShortArray {
        val durationMs = 28
        val sampleCount = (sampleRate * durationMs / 1_000.0).roundToInt()
        return ShortArray(sampleCount) { index ->
            val seconds = index.toDouble() / sampleRate
            val progress = index.toDouble() / sampleCount

            // A short bright transient makes the beat feel immediate; the low body adds weight.
            val bodyEnvelope = exp(-10.0 * progress)
            val transientEnvelope = exp(-38.0 * progress)
            val lowBody = 0.86 * sin(2.0 * PI * 220.0 * seconds)
            val lowHarmonic = 0.42 * sin(2.0 * PI * 440.0 * seconds)
            val transient = 0.72 * sin(2.0 * PI * 2_200.0 * seconds)
            val sample = (lowBody + lowHarmonic) * bodyEnvelope + transient * transientEnvelope

            (sample * 0.96 * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private data class ClickTrack(
        val track: AudioTrack,
        val samples: ShortArray,
    )
}
