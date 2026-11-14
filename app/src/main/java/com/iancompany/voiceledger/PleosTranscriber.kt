package com.iancompany.voiceledger

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Supplies our foreground recording to the official on-device service. No external STT fallback. */
internal class PleosTranscriber(private val context: Context) : VoiceInput.Decoder {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active: Session? = null

    private class Session {
        val done = CountDownLatch(1)
        val finished = AtomicBoolean()
        var sdk: SpeechToText? = null // SDK access is confined to the main thread.
        var listener: ResultListener? = null
        @Volatile var text = ""
        @Volatile var failure: Exception? = null
        fun finish(value: String = "", error: Exception? = null) {
            if (finished.compareAndSet(false, true)) {
                text = value
                failure = error
                done.countDown()
            }
        }
    }

    override fun decode(samples: FloatArray, cancelled: () -> Boolean): String {
        check(Looper.myLooper() != Looper.getMainLooper())
        if (cancelled()) return ""
        val pcm = PcmAudio.wav(samples).copyOfRange(44, 44 + samples.size * 2)
        val session = Session()
        active = session
        main.post {
            if (session.finished.get() || cancelled()) session.finish()
            else try {
                val sdk = SpeechToText(context, Mode.ON_DEVICE)
                session.sdk = sdk
                val listener = object : ResultListener {
                    override fun onReady() = Unit
                    override fun onStartedRecognition() = Unit
                    override fun onUpdated(stt: String, completed: Boolean) {
                        if (completed) session.finish(stt)
                    }
                    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    // Pleos can deliver the final transcript AFTER this callback.
                    override fun onEndedRecognition() = Unit
                    override fun onError(error: ErrorType) {
                        session.finish(error = IllegalStateException("Pleos STT failed: $error"))
                    }
                }
                session.listener = listener
                sdk.initialize(object : OnInitializeListener {
                    override fun onSuccess() { main.post {
                        if (!session.finished.get() && !cancelled()) try {
                            sdk.setLocale(Locale.KOREA)
                            sdk.addListener(listener)
                            sdk.sendAudio(pcm)
                            sdk.completeAudioSend()
                        } catch (error: Exception) { session.finish(error = error) }
                        else session.finish()
                    } }
                    override fun onFailure(error: ErrorType) {
                        session.finish(error = IllegalStateException("Pleos STT unavailable: $error"))
                    }
                })
            } catch (error: Exception) { session.finish(error = error) }
        }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!session.done.await(100, TimeUnit.MILLISECONDS)) {
                if (cancelled()) { session.finish(); return "" }
                check(System.nanoTime() < deadline) { "Pleos STT timed out" }
            }
            if (cancelled()) return ""
            session.failure?.let { throw it }
            return session.text.trim()
        } finally {
            session.finish()
            release(session)
            if (active === session) active = null
        }
    }

    private fun release(session: Session) { main.post {
        session.sdk?.let { sdk ->
            session.listener?.let { runCatching { sdk.removeListener(it) } }
            runCatching { sdk.stop() }
            runCatching { sdk.release() }
        }
        session.sdk = null
    } }

    override fun cancel() {
        active?.let { it.finish(); release(it) }
    }
    override fun close() = cancel()
}
