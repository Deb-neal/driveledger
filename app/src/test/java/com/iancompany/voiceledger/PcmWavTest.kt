package com.iancompany.voiceledger

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWavTest {
    @Test fun wavPreservesSampleRateSizeAndSignedSamples() {
        val samples = FloatArray(4000)
        samples[0] = -1f; samples[1] = 1f; samples[2] = 0.5f
        val wav = PcmAudio.wav(samples)
        val bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(8036, bytes.getInt(4))
        assertEquals(16000, bytes.getInt(24))
        assertEquals(8000, bytes.getInt(40))
        assertEquals((-32768).toShort(), bytes.getShort(44))
        assertEquals(32767.toShort(), bytes.getShort(46))
        assertEquals(16384.toShort(), bytes.getShort(48))
        assertEquals(8044, wav.size)
    }
    @Test fun invalidLengthAndNonFiniteSamplesAreRejected() {
        for (samples in listOf(FloatArray(0), FloatArray(128001), FloatArray(4000) { Float.NaN })) {
            try { PcmAudio.wav(samples); fail("Must reject invalid audio") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
