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

/** Explicit opt-in only: one synthetic API call; never opens the microphone or saves a record. */
class OnlineVoiceTest {
    private fun field(instance: Any, name: String) = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun await(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25000
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(50)
        }
        assertTrue("Online UI timed out", check())
    }

    @Test fun syntheticVoiceUsesOnlineEngineAndRequiresConfirmation() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveOnline") == "true" && BuildConfig.ONLINE_VOICE)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val initial = ExpenseStore(context).use { it.month(YearMonth.now()) }
        val wav = instrumentation.context.assets.open("online-fuel30k.wav").use { it.readBytes() }
        // Fixture is a PCM WAV with arbitrary RIFF metadata; find its data chunk.
        val bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        while (String(wav, offset, 4, Charsets.US_ASCII) != "data") offset += 8 + bytes.getInt(offset + 4) + (bytes.getInt(offset + 4) % 2)
        val pcm = ShortArray(bytes.getInt(offset + 4) / 2) { bytes.getShort(offset + 8 + it * 2) }
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
                assertEquals(it.getString(R.string.voice_online_start), it.findViewById<Button>(R.id.voice_start).text.toString())
                it.findViewById<Button>(R.id.voice_start).performClick()
                assertFalse(voice.busy) // Disclosure must come before recording.
            }
            instrumentation.runOnMainSync {
                WindowInspector.getGlobalWindowViews().mapNotNull { it.findViewById<Button>(android.R.id.button1) }.single().performClick()
            }
            await { count.get() == pcm.size }
            scenario.onActivity { it.findViewById<Button>(R.id.voice_start).performClick() }
            val expectFailure = args.getString("expectFailure") == "true"
            await {
                var done = false
                scenario.onActivity {
                    done = if (expectFailure) it.findViewById<TextView>(R.id.voice_status).text.toString() == it.getString(R.string.voice_online_failed)
                        else it.findViewById<EditText>(R.id.amount).text.toString() == "30000"
                }
                done
            }
            scenario.onActivity {
                if (!expectFailure) assertEquals(ExpenseCategory.FUEL.ordinal + 1, it.findViewById<Spinner>(R.id.category).selectedItemPosition)
                else assertEquals("", it.findViewById<EditText>(R.id.amount).text.toString())
            }
            assertEquals(initial, ExpenseStore(context).use { it.month(YearMonth.now()) })
        }
    }
}
