package com.iancompany.voiceledger

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.YearMonth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Synthetic PCM/fake sources only: these tests never open the host microphone. */
class VoiceInputTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for voice state", check())
    }
    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    private fun ready(scenario: ActivityScenario<MainActivity>) = await {
        var ready = false
        scenario.onActivity { ready = it.findViewById<Button>(R.id.record_expense)?.isEnabled == true }
        ready
    }
    private class Samples(private val pcm: ShortArray) : VoiceInput.Source {
        @Volatile var count = 0
        val closed = AtomicBoolean()
        override fun start() = Unit
        override fun read(buffer: ShortArray, count: Int): Int {
            val length = minOf(count, pcm.size - this.count)
            pcm.copyInto(buffer, 0, this.count, this.count + length)
            this.count += length
            return length
        }
        override fun close() { closed.set(true) }
    }
    private fun installSource(activity: MainActivity, source: Samples): VoiceInput {
        val voice = field(activity, "voice") as VoiceInput
        // Keep the product Pleos decoder; lifecycle checks must not load the retired Whisper engine.
        // Only the instrumentation harness replaces hardware input; no exported product test switch.
        voice.javaClass.getDeclaredField("sourceFactory").apply { isAccessible = true }.set(voice, { source })
        return voice
    }

    @Test fun syntheticVoiceReachesConfirmationAndOnlyExplicitSaveWritesOneExpense() {
        val bytes = instrumentation.context.assets.open("synthetic-parking.pcm").use { it.readBytes() }
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val pcm = ShortArray(shorts.remaining()).also { shorts.get(it) }
        val source = Samples(pcm)
        val initial = ExpenseStore(context).use { it.month(YearMonth.now()) }
        var createdId: String? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                ready(scenario)
                scenario.onActivity {
                    it.findViewById<Button>(R.id.record_expense).performClick()
                    createdId = field(field(it, "draft")!!, "id") as String
                    installSource(it, source)
                    it.findViewById<Button>(R.id.voice_start).performClick()
                }
                await { source.count == pcm.size }
                scenario.onActivity { it.findViewById<Button>(R.id.voice_start).performClick() }
                await {
                    var read = false
                    scenario.onActivity { read = it.findViewById<EditText>(R.id.amount)?.text.toString() == "3000" }
                    read
                }
                assertTrue(source.closed.get())
                assertEquals(initial.totalWon, ExpenseStore(context).use { it.month(YearMonth.now()).totalWon })
                scenario.onActivity {
                    assertEquals(ExpenseCategory.PARKING.ordinal + 1,
                        it.findViewById<android.widget.Spinner>(R.id.category).selectedItemPosition)
                    it.findViewById<EditText>(R.id.amount).setText("4500")
                    it.findViewById<Button>(R.id.save_expense).performClick()
                    it.findViewById<Button>(R.id.save_expense).performClick()
                }
                ready(scenario)
                val saved = ExpenseStore(context).use { it.month(YearMonth.now()) }
                assertEquals(initial.totalWon + 4500, saved.totalWon)
                assertEquals(initial.entries.size + 1, saved.entries.size)
                assertEquals(4500L, saved.entries.single { it.id == createdId }.amountWon)
            }
        } finally {
            ExpenseStore(context).use { db ->
                db.month(YearMonth.now()).entries.find { it.id == createdId }?.let { db.delete(it.id, it.revision) }
            }
        }
    }

    @Test fun cancellingDuringDecodeRejectsLateResultsAndPreservesData() {
        val decoding = CountDownLatch(1)
        val decoderCancelled = AtomicBoolean()
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val source = Samples(ShortArray(16000) { 2000 })
        var delivered = false
        lateinit var voice: VoiceInput
        main {
            voice = VoiceInput(context, { _, _ -> }, { delivered = true }, { source }, {
                object : VoiceInput.Decoder {
                    override fun decode(samples: FloatArray, cancelled: () -> Boolean): String {
                        decoding.countDown()
                        release.await(10, TimeUnit.SECONDS)
                        finished.countDown()
                        return "주차 삼천 원"
                    }
                    override fun cancel() { decoderCancelled.set(true) }
                    override fun close() = Unit
                }
            })
            voice.start()
        }
        try {
            await { source.count == 16000 }
            main { voice.finishRecording() }
            assertTrue(decoding.await(10, TimeUnit.SECONDS))
            assertTrue("Audio source must close before decoding", source.closed.get())
            main { voice.cancel(); assertTrue(decoderCancelled.get()) }
            release.countDown()
            assertTrue(finished.await(10, TimeUnit.SECONDS))
            Thread.sleep(100)
            main { assertFalse(delivered); assertEquals(VoiceInput.State.IDLE, voice.state) }
        } finally { release.countDown(); main { voice.close() } }
    }

    @Test fun leavingForegroundStopsCaptureWithoutChangingExistingDraft() {
        val source = Samples(ShortArray(0))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ready(scenario)
            lateinit var voice: VoiceInput
            scenario.onActivity {
                it.findViewById<Button>(R.id.record_expense).performClick()
                it.findViewById<EditText>(R.id.amount).setText("9900")
                voice = installSource(it, source)
                it.findViewById<Button>(R.id.voice_start).performClick()
            }
            await { var recording = false; main { recording = voice.state == VoiceInput.State.RECORDING }; recording }
            scenario.moveToState(Lifecycle.State.CREATED)
            await { source.closed.get() }
            main { assertFalse(voice.busy) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals("9900", it.findViewById<EditText>(R.id.amount).text.toString()) }
        }
    }

    @Test fun deniedPermissionNeverCreatesRecorder() {
        val denied = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String) = if (permission == Manifest.permission.RECORD_AUDIO)
                PackageManager.PERMISSION_DENIED else super.checkSelfPermission(permission)
        }
        var message = 0
        main {
            VoiceInput(denied, { _, id -> message = id }, { fail("Unexpected result") }, { error("Recorder must not be created") }).use {
                it.start()
                assertFalse(it.busy)
                assertEquals(R.string.voice_permission_denied, message)
            }
        }
    }

    @Test fun losingVehicleStateCancelsCaptureAndBlocksSaving() {
        val source = Samples(ShortArray(0))
        val initial = ExpenseStore(context).use { it.month(YearMonth.now()).totalWon }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ready(scenario)
            lateinit var voice: VoiceInput
            scenario.onActivity {
                it.findViewById<Button>(R.id.record_expense).performClick()
                it.findViewById<EditText>(R.id.amount).setText("9900")
                voice = installSource(it, source)
                it.findViewById<Button>(R.id.voice_start).performClick()
            }
            await { var recording = false; main { recording = voice.state == VoiceInput.State.RECORDING }; recording }
            scenario.onActivity {
                (field(it, "parkedGate") as ParkedGate).stop()
                assertFalse(voice.busy)
                assertFalse(it.findViewById<Button>(R.id.voice_start).isEnabled)
                assertFalse(it.findViewById<Button>(R.id.save_expense).isEnabled)
                assertEquals("9900", it.findViewById<EditText>(R.id.amount).text.toString())
            }
            await { source.closed.get() }
            assertEquals(initial, ExpenseStore(context).use { it.month(YearMonth.now()).totalWon })
        }
    }

    @Test fun silenceClosesSourceWithoutDecodingOrReturningAnExpense() {
        val source = Samples(ShortArray(16000))
        var message = 0
        lateinit var voice: VoiceInput
        main {
            voice = VoiceInput(context, { _, id -> message = id }, { fail("Silence produced text") }, { source }, {
                object : VoiceInput.Decoder {
                    override fun decode(samples: FloatArray, cancelled: () -> Boolean): String = error("Silence must not be decoded")
                    override fun close() = Unit
                }
            })
            voice.start()
        }
        try {
            await { source.count == 16000 }
            main { voice.finishRecording() }
            await { var idle = false; main { idle = !voice.busy }; idle }
            main { assertEquals(R.string.voice_no_speech, message) }
            assertTrue(source.closed.get())
        } finally { main { voice.close() } }
    }

    @Test fun audioFocusLossStopsCaptureAndKeepsDraft() {
        val source = Samples(ShortArray(0))
        val audio = context.getSystemService(AudioManager::class.java)
        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                ready(scenario)
                lateinit var voice: VoiceInput
                scenario.onActivity {
                    it.findViewById<Button>(R.id.record_expense).performClick()
                    it.findViewById<EditText>(R.id.amount).setText("9900")
                    voice = installSource(it, source)
                    it.findViewById<Button>(R.id.voice_start).performClick()
                }
                await { var recording = false; main { recording = voice.state == VoiceInput.State.RECORDING }; recording }
                main { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(other)) }
                await { source.closed.get() }
                scenario.onActivity {
                    assertFalse(voice.busy)
                    assertEquals(it.getString(R.string.voice_interrupted),
                        it.findViewById<android.widget.TextView>(R.id.voice_status).text.toString())
                    assertEquals("9900", it.findViewById<EditText>(R.id.amount).text.toString())
                }
            }
        } finally { main { audio.abandonAudioFocusRequest(other) } }
    }
}
