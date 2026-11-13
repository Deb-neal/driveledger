package com.iancompany.voiceledger

internal object PcmAudio {
    const val SAMPLE_RATE = 16000
    const val MAX_SAMPLES = SAMPLE_RATE * 8

    fun wav(samples: FloatArray): ByteArray {
        require(samples.size in 4000..MAX_SAMPLES && samples.all { it.isFinite() && it in -1f..1f })
        val bytes = samples.size * 2
        return java.nio.ByteBuffer.allocate(44 + bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + bytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(1); putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2)
            putShort(2); putShort(16); put("data".toByteArray(Charsets.US_ASCII)); putInt(bytes)
            samples.forEach { putShort((it * 32768f).toInt().coerceIn(-32768, 32767).toShort()) }
        }.array()
    }

    // shortcut: only rejects near-silence, not a speech detector. Calibrate with cabin recordings.
    fun hasSignal(samples: FloatArray): Boolean {
        if (samples.size !in 4000..MAX_SAMPLES || samples.any { !it.isFinite() || it !in -1f..1f }) return false
        return samples.sumOf { it.toDouble() * it } / samples.size > 0.00001
    }
}
