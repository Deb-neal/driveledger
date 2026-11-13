package com.iancompany.voiceledger

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OfflineSpeechTest {
    @Test fun bundledModelTranscribesSyntheticParkingExpenseAndRejectsSilence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("synthetic-parking.pcm").use { it.readBytes() }
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(shorts.remaining()) { shorts.get() / 32768f }
        OfflineTranscriber(instrumentation.targetContext.assets).use { transcriber ->
            assertEquals("", transcriber.transcribe(FloatArray(16000)))
            val result = transcriber.transcribe(samples)
            Log.i("SpeechProbe", "offline synthetic parking=$result")
            assertTrue("Expected parking category: $result", result.replace(" ", "").contains("주차"))
            assertTrue("Expected 3000 KRW: $result", Regex("(?:^|[^0-9])(?:삼천|3천|3000|3,000)원(?:$|[^0-9])")
                .containsMatchIn(result.replace(" ", "")))
        }
    }
}
