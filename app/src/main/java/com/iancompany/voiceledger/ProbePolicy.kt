package com.iancompany.voiceledger

/** Unknown or stale state must never unlock recording on a real vehicle. */
internal object ProbePolicy {
    fun mayRecord(isEmulator: Boolean, parked: Boolean?, ageMillis: Long): Boolean =
        isEmulator || (parked == true && ageMillis in 0..3_000)

    fun distanceLabel(value: Float?): String = when {
        value == null || !value.isFinite() || value < 0 -> "주행거리 미상"
        else -> "SDK 주행거리 원시 값: $value (단위 검증 전)"
    }
}
