package com.iancompany.voiceledger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** One foreground session. Audio remains in memory; cancellation invalidates every pending result. */
internal class VoiceInput(
    private val context: Context,
    private val changed: (State, Int) -> Unit,
    private val result: (String) -> Unit,
    private val sourceFactory: () -> Source = { Microphone(context) },
    private val decoderFactory: () -> Decoder = {
        if (BuildConfig.DEBUG && BuildConfig.LOCAL_ONLINE_VOICE) OnlineTranscriber() else {
            val engine = OfflineTranscriber(context.assets)
            object : Decoder {
                override fun decode(samples: FloatArray, cancelled: () -> Boolean) = if (cancelled()) "" else engine.transcribe(samples)
                override fun close() = engine.close()
            }
        }
    },
) : AutoCloseable {
    internal interface Source : AutoCloseable {
        fun start()
        fun read(buffer: ShortArray, count: Int): Int
    }
    internal interface Decoder : AutoCloseable {
        fun decode(samples: FloatArray, cancelled: () -> Boolean): String
        fun cancel() = Unit
    }
    enum class State { IDLE, PREPARING, RECORDING, TRANSCRIBING }
    var state = State.IDLE
        private set
    val busy get() = state != State.IDLE
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    @Volatile private var finish = false
    private var closed = false
    @Volatile private var decoder: Decoder? = null // Native release stays on worker; online cancel disconnects the socket.
    private val audio = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private val timeout = Runnable { cancel(R.string.voice_timeout) }

    fun start() {
        if (closed || busy) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            changed(State.IDLE, R.string.voice_permission_denied)
            return
        }
        val token = generation.incrementAndGet()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0 && generation.get() == token) cancel(R.string.voice_interrupted)
            }, main).build()
        val granted = runCatching { audio.requestAudioFocus(request) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            runCatching { audio.abandonAudioFocusRequest(request) }
            changed(State.IDLE, R.string.voice_focus_denied)
            return
        }
        focus = request
        finish = false
        update(State.PREPARING, R.string.voice_preparing)
        main.postDelayed(timeout, 30_000)
        fun post(block: () -> Unit) = main.post {
            if (!closed && generation.get() == token) block()
        }
        worker.execute {
            try {
                val engine = decoder ?: decoderFactory().also { decoder = it }
                if (generation.get() != token) return@execute
                val pcm = FloatArray(PcmAudio.MAX_SAMPLES)
                var count = 0
                sourceFactory().use { source ->
                    if (generation.get() != token) return@use
                    source.start()
                    post { update(State.RECORDING, R.string.voice_listening) }
                    val buffer = ShortArray(1024)
                    val deadline = SystemClock.elapsedRealtime() + 8_000
                    // Nonblocking reads keep cancel/lifecycle release bounded even on silent devices.
                    while (!finish && generation.get() == token && count < pcm.size && SystemClock.elapsedRealtime() < deadline) {
                        val read = source.read(buffer, minOf(buffer.size, pcm.size - count))
                        check(read >= 0) { "Microphone read failed" }
                        if (read == 0) { Thread.sleep(10); continue }
                        for (index in 0 until read) pcm[count++] = buffer[index] / 32768f
                    }
                }
                if (generation.get() != token) return@execute
                post { abandonFocus(); update(State.TRANSCRIBING, R.string.voice_transcribing) }
                val text = if (PcmAudio.hasSignal(pcm.copyOf(count))) engine.decode(pcm.copyOf(count)) { generation.get() != token } else ""
                post {
                    main.removeCallbacks(timeout)
                    update(State.IDLE, if (text.isBlank()) R.string.voice_no_speech else R.string.voice_ready)
                    if (text.isNotBlank()) result(text)
                }
            } catch (_: Exception) {
                post { main.removeCallbacks(timeout); abandonFocus(); update(State.IDLE,
                    if (BuildConfig.LOCAL_ONLINE_VOICE) R.string.voice_online_failed else R.string.voice_failed) }
            } catch (_: LinkageError) {
                post { main.removeCallbacks(timeout); abandonFocus(); update(State.IDLE, R.string.voice_failed) }
            }
        }
    }

    fun finishRecording() {
        if (state == State.RECORDING) {
            finish = true
            update(State.TRANSCRIBING, R.string.voice_transcribing)
        }
    }

    fun cancel(message: Int = R.string.voice_cancelled) {
        generation.incrementAndGet()
        runCatching { decoder?.cancel() }
        finish = true
        main.removeCallbacks(timeout)
        abandonFocus()
        update(State.IDLE, message)
    }

    private fun update(value: State, message: Int) { state = value; changed(value, message) }
    private fun abandonFocus() {
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        focus = null
    }

    override fun close() {
        cancel()
        closed = true
        worker.execute { decoder?.close(); decoder = null }
        worker.shutdown()
    }

    private class Microphone(context: Context) : Source {
        private val recorder: AudioRecord
        init {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Microphone permission revoked")
            }
            val minimum = AudioRecord.getMinBufferSize(PcmAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, PcmAudio.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096))
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                error("Microphone unavailable")
            }
        }
        override fun start() {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        }
        override fun read(buffer: ShortArray, count: Int) = recorder.read(buffer, 0, count, AudioRecord.READ_NON_BLOCKING)
        override fun close() {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }
}
