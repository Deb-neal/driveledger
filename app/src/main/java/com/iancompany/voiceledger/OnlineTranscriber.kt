package com.iancompany.voiceledger

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

/** shortcut: debug-only loopback relay through adb reverse; deploy HTTPS before submitting online APKs. */
internal class OnlineTranscriber : VoiceInput.Decoder {
    @Volatile private var connection: HttpURLConnection? = null

    override fun decode(samples: FloatArray, cancelled: () -> Boolean): String {
        check(BuildConfig.DEBUG && BuildConfig.LOCAL_ONLINE_VOICE)
        val wav = PcmAudio.wav(samples)
        val current = URL("http://127.0.0.1:18765/transcribe").openConnection() as HttpURLConnection
        connection = current
        try {
            if (cancelled()) return ""
            current.requestMethod = "POST"
            current.connectTimeout = 3000
            current.readTimeout = 15000
            current.instanceFollowRedirects = false
            current.doOutput = true
            current.setRequestProperty("Content-Type", "audio/wav")
            current.setRequestProperty("X-DriveLedger-Local", "1")
            current.setFixedLengthStreamingMode(wav.size)
            if (cancelled()) return ""
            current.outputStream.use { if (!cancelled()) it.write(wav) }
            if (cancelled()) return ""
            if (current.responseCode != 200) throw IOException("Transcription unavailable")
            val body = current.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (output.size() <= 8192) {
                    val count = stream.read(buffer, 0, minOf(buffer.size, 8193 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (body.size > 8192) throw IOException("Transcription response too large")
            val json = JSONObject(body.toString(Charsets.UTF_8))
            val text = json.opt("text")
            if (text !is String || text.length > 200) throw IOException("Invalid transcription")
            return if (cancelled()) "" else text.trim()
        } finally {
            current.disconnect()
            if (connection === current) connection = null
        }
    }

    override fun cancel() { connection?.disconnect() }
    override fun close() = cancel()
}
