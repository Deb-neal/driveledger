package com.iancompany.voiceledger

import org.junit.Assert.*
import org.junit.Test

class ProbePolicyTest {
    @Test fun unknownMovingOrStaleVehicleNeverAllowsRecording() {
        assertFalse(ProbePolicy.mayRecord(false, null, 0))
        assertFalse(ProbePolicy.mayRecord(false, false, 0))
        assertFalse(ProbePolicy.mayRecord(false, true, 3001))
        assertFalse(ProbePolicy.mayRecord(false, true, -1))
        assertTrue(ProbePolicy.mayRecord(false, true, 2000))
    }
    @Test fun emulatorCanProbeWithoutRealVehicleState() {
        assertTrue(ProbePolicy.mayRecord(true, null, 99999))
    }
    @Test fun absentOrInvalidDistanceIsNotZero() {
        for (value in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            assertEquals("주행거리 미상", ProbePolicy.distanceLabel(value))
        }
        assertTrue(ProbePolicy.distanceLabel(0f).contains("0.0"))
    }
}
