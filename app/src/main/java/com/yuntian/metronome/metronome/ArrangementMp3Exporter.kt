package com.yuntian.metronome.metronome

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class ArrangementMp3Exporter(context: Context) {
    private val applicationContext = context.applicationContext
    private val clickSamples by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadClickSamples(applicationContext)
    }

    suspend fun exportToFile(
        rawChanges: List<ArrangementChange>,
        options: ArrangementExportOptions,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val changes = sanitizeArrangementChanges(rawChanges)
        require(changes.isNotEmpty())
        val renderer = ArrangementExportPcmRenderer(
            rawChanges = changes,
            options = options,
            clickSamples = clickSamples,
            maximumFrames = MAX_EXPORT_FRAMES,
        )

        outputFile.parentFile?.mkdirs()
        var lastReportedProgress = -1f
        NativeLameMp3Encoder().use { encoder ->
            FileOutputStream(outputFile, false).buffered().use { output ->
                while (renderer.hasRemainingFrames) {
                    coroutineContext.ensureActive()
                    val pcm = renderer.render(ENCODE_BLOCK_FRAMES)
                    val encoded = encoder.encode(pcm)
                    if (encoded.isNotEmpty()) output.write(encoded)
                    val progress = renderer.progress * ENCODING_PROGRESS_WEIGHT
                    if (progress - lastReportedProgress >= MIN_PROGRESS_DELTA || progress >= 1f) {
                        onProgress(progress)
                        lastReportedProgress = progress
                    }
                }
                coroutineContext.ensureActive()
                val flushed = encoder.flush()
                if (flushed.isNotEmpty()) output.write(flushed)
                output.flush()
            }
        }
        onProgress(ENCODING_PROGRESS_WEIGHT)
    }

    private companion object {
        const val ENCODE_BLOCK_FRAMES = 4_608
        const val ENCODING_PROGRESS_WEIGHT = 0.95f
        const val MIN_PROGRESS_DELTA = 0.0025f
        const val MAX_EXPORT_FRAMES = ARRANGEMENT_EXPORT_MAX_DURATION_SECONDS *
            METRONOME_PCM_SAMPLE_RATE
    }
}

internal class NativeLameMp3Encoder : AutoCloseable {
    private var handle: Long = nativeCreate()

    fun encode(samples: ShortArray): ByteArray {
        check(handle != 0L) { "LAME encoder is closed" }
        return nativeEncode(handle, samples)
    }

    fun flush(): ByteArray {
        check(handle != 0L) { "LAME encoder is closed" }
        return nativeFlush(handle)
    }

    override fun close() {
        val current = handle
        if (current == 0L) return
        handle = 0L
        nativeClose(current)
    }

    private external fun nativeCreate(): Long
    private external fun nativeEncode(handle: Long, samples: ShortArray): ByteArray
    private external fun nativeFlush(handle: Long): ByteArray
    private external fun nativeClose(handle: Long)

    private companion object {
        init {
            System.loadLibrary("metronome_mp3")
        }
    }
}
