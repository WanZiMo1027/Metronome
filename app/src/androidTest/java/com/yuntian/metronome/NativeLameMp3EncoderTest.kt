package com.yuntian.metronome

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuntian.metronome.metronome.NativeLameMp3Encoder
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeLameMp3EncoderTest {
    @Test
    fun lameProducesPlayableStereoMpegAudioAndCanBeRecreated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pcm = oneSecondStereoTone()

        repeat(2) { attempt ->
            val output = File(context.cacheDir, "lame-test-$attempt.mp3")
            try {
                output.outputStream().use { stream ->
                    NativeLameMp3Encoder().use { encoder ->
                        stream.write(encoder.encode(pcm))
                        stream.write(encoder.flush())
                    }
                }
                assertTrue(output.length() > 10_000L)

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(output.absolutePath)
                    assertTrue(extractor.trackCount > 0)
                    val format = extractor.getTrackFormat(0)
                    assertEquals("audio/mpeg", format.getString(MediaFormat.KEY_MIME))
                    assertEquals(44_100, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                    assertEquals(2, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    assertTrue(format.getLong(MediaFormat.KEY_DURATION) > 900_000L)
                } finally {
                    extractor.release()
                }
            } finally {
                output.delete()
            }
        }
    }

    private fun oneSecondStereoTone(): ShortArray {
        val frames = 44_100
        return ShortArray(frames * 2) { sampleIndex ->
            val frame = sampleIndex / 2
            (sin(2.0 * PI * 440.0 * frame / 44_100.0) * 8_000).toInt().toShort()
        }
    }
}
