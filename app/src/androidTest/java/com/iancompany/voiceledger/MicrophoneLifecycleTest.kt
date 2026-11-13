package com.iancompany.voiceledger

import android.media.AudioManager
import android.view.View
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class MicrophoneLifecycleTest {
    @Test fun leavingForegroundReleasesMicrophone() {
        assumeTrue("Run only on emulator with host audio disabled",
            InstrumentationRegistry.getArguments().getString("hostAudioDisabled") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(AudioManager::class.java)
        ActivityScenario.launch(ProbeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val matches = ArrayList<View>()
                activity.window.decorView.findViewsWithText(matches, "오프라인 음성 테스트", View.FIND_VIEWS_WITH_TEXT)
                matches.filterIsInstance<Button>().single().performClick()
            }
            val startDeadline = System.currentTimeMillis() + 15_000
            while (audio.activeRecordingConfigurations.isEmpty() && System.currentTimeMillis() < startDeadline) Thread.sleep(100)
            assertTrue("Microphone did not start", audio.activeRecordingConfigurations.isNotEmpty())
            scenario.moveToState(Lifecycle.State.CREATED)
            val stopDeadline = System.currentTimeMillis() + 2_000
            while (audio.activeRecordingConfigurations.isNotEmpty() && System.currentTimeMillis() < stopDeadline) Thread.sleep(50)
            assertTrue("Microphone remained active after onStop", audio.activeRecordingConfigurations.isEmpty())
        }
    }
}
