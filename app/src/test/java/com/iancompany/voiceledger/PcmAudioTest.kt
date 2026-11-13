package com.iancompany.voiceledger

import org.junit.Assert.*
import org.junit.Test

class PcmAudioTest {
    @Test fun silenceAndInvalidAudioNeverReachDecoder() {
        assertFalse(PcmAudio.hasSignal(FloatArray(16000)))
        assertFalse(PcmAudio.hasSignal(floatArrayOf(0.5f)))
        assertFalse(PcmAudio.hasSignal(FloatArray(4000) { Float.NaN }))
        assertFalse(PcmAudio.hasSignal(FloatArray(4000) { 2f }))
        assertFalse(PcmAudio.hasSignal(FloatArray(PcmAudio.MAX_SAMPLES + 1) { 0.5f }))
    }
    @Test fun nonSilentFinitePcmPasses() {
        assertTrue(PcmAudio.hasSignal(FloatArray(16000) { if (it % 2 == 0) 0.1f else -0.1f }))
    }
}
