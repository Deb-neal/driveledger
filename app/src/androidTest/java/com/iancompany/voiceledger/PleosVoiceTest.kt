package com.iancompany.voiceledger

import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.view.inspector.WindowInspector
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.YearMonth

/** Real official ON_DEVICE SDK, synthetic PCM only; never opens the microphone. */
class PleosVoiceTest {
    private fun field(instance: Any, name: String) = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun await(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25000
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(50)
        }
        assertTrue("Pleos UI timed out", check())
    }

    @Test fun officialVoiceFillsDraftWithoutSaving() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val initial = ExpenseStore(context).use { it.month(YearMonth.now()) }
        val pcmBytes = instrumentation.context.assets.open("synthetic-parking.pcm").use { it.readBytes() }
        val bytes = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = ShortArray(pcmBytes.size / 2) { bytes.getShort(it * 2) }
        val count = java.util.concurrent.atomic.AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.findViewById<Button>(R.id.record_expense)?.isEnabled == true }; ready }
            scenario.onActivity {
                it.findViewById<Button>(R.id.record_expense).performClick()
                val voice = field(it, "voice").get(it) as VoiceInput
                field(voice, "sourceFactory").set(voice, {
                    object : VoiceInput.Source {
                        override fun start() = Unit
                        override fun close() = Unit
                        override fun read(buffer: ShortArray, requested: Int): Int {
                            val n = minOf(requested, pcm.size - count.get())
                            pcm.copyInto(buffer, 0, count.get(), count.get() + n); count.addAndGet(n)
                            return n
                        }
                    }
                })
                assertEquals(it.getString(R.string.voice_start), it.findViewById<Button>(R.id.voice_start).text.toString())
                it.findViewById<Button>(R.id.voice_start).performClick()
                assertTrue(voice.busy)
            }
            await { count.get() == pcm.size }
            scenario.onActivity { it.findViewById<Button>(R.id.voice_start).performClick() }
            await {
                var done = false
                scenario.onActivity { done = it.findViewById<EditText>(R.id.amount).text.toString() == "3000" }
                done
            }
            scenario.onActivity {
                assertEquals(ExpenseCategory.PARKING.ordinal + 1, it.findViewById<Spinner>(R.id.category).selectedItemPosition)
            }
            assertEquals(initial, ExpenseStore(context).use { it.month(YearMonth.now()) })
        }
    }
}
