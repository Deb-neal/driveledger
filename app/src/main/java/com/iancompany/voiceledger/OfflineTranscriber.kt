package com.iancompany.voiceledger

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/** Owned by one worker thread; raw audio stays in memory and is never uploaded. */
internal class OfflineTranscriber(assets: AssetManager) : AutoCloseable {
    private val recognizer = OfflineRecognizer(assets, OfflineRecognizerConfig(
        modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "asr/tiny-encoder.int8.onnx",
                decoder = "asr/tiny-decoder.int8.onnx",
                language = "ko",
                task = "transcribe",
            ),
            tokens = "asr/tiny-tokens.txt",
            numThreads = 2,
            provider = "cpu",
        ),
    ))

    fun transcribe(samples: FloatArray): String {
        if (!PcmAudio.hasSignal(samples)) return ""
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, PcmAudio.SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally { stream.release() }
    }

    override fun close() = recognizer.release()
}
