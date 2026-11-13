package com.iancompany.voiceledger

import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit capability probe. Uses synthetic PCM, never the host microphone. */
class PlatformSpeechProbeTest {
    @Test fun transcribesSyntheticKoreanExpenseViaPlatformService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Opt-in platform capability experiment", InstrumentationRegistry.getArguments().getString("probePlatformSpeech") == "true")
        val done = CountDownLatch(1)
        val pipe = ParcelFileDescriptor.createPipe()
        var outcome = "No callback within 20 seconds"
        var recognizer: SpeechRecognizer? = null
        try {
            instrumentation.runOnMainSync {
                recognizer = SpeechRecognizer.createSpeechRecognizer(instrumentation.targetContext).also { speech ->
                    speech.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { Log.i("SpeechProbe", "ready") }
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onError(error: Int) { outcome = "error=$error"; done.countDown() }
                        override fun onResults(results: Bundle?) {
                            outcome = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.joinToString() ?: "empty"
                            done.countDown()
                        }
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                    speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                    })
                }
            }
            val writer = Thread {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        instrumentation.context.assets.open("synthetic-fuel.pcm").use { it.copyTo(output) }
                    }
                } catch (_: java.io.IOException) { /* Service may reject injected audio. */ }
            }.apply { start() }
            done.await(20, TimeUnit.SECONDS)
            Log.i("SpeechProbe", "synthetic outcome=$outcome")
            assertTrue("Platform Korean ASR unavailable: $outcome", outcome.contains("주유"))
            writer.join(1000)
        } finally {
            instrumentation.runOnMainSync { recognizer?.destroy() }
            pipe.forEach { it.close() }
        }
    }
}
