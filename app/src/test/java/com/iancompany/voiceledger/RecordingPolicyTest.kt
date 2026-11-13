package com.iancompany.voiceledger

import org.junit.Assert.*
import org.junit.Test

class RecordingPolicyTest {
    @Test fun requiresFreshParkedStateWithoutEmulatorException() {
        assertTrue(RecordingPolicy.allowsInput(true, 0))
        assertTrue(RecordingPolicy.allowsInput(true, 3000))
        assertFalse(RecordingPolicy.allowsInput(true, 3001))
        assertFalse(RecordingPolicy.allowsInput(true, -1))
        for (parked in listOf(false, null)) {
            assertFalse(RecordingPolicy.allowsInput(parked, 0))
        }
    }
}
