package com.iancompany.voiceledger

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear

internal object RecordingPolicy {
    fun allowsInput(parked: Boolean?, ageMillis: Long): Boolean = parked == true && ageMillis in 0..3_000
}

/** Product gate: emulator and hardware use the same fresh SDK state, with no permissive fallback. */
internal class ParkedGate(private val context: Context, private val changed: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var generation = 0
    private var request = 0
    private var active = false
    private var parked: Boolean? = null
    private var observedAt = 0L
    val allowed get() = active && RecordingPolicy.allowsInput(parked, SystemClock.elapsedRealtime() - observedAt)

    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            changed() // Expire the last P result even when the SDK stops replying.
            val token = generation
            val query = ++request
            val requestedAt = SystemClock.elapsedRealtime()
            fun deliver(value: Boolean?) = handler.post {
                if (active && generation == token && query == request) {
                    parked = value
                    observedAt = requestedAt
                    changed()
                }
            }
            try {
                vehicle?.getGear()?.getCurrentGearSelectionState(
                    onComplete = { deliver(it == VehicleGear.GEAR_PARK) },
                    onFailed = { deliver(null) })
            } catch (_: Exception) { deliver(null) }
            handler.postDelayed(this, 1_000)
        }
    }

    fun start() {
        if (active) return
        active = true
        generation++
        parked = null
        vehicle = try { Vehicle(context).also { it.initialize() } } catch (_: Exception) { null }
        handler.post(poll)
    }

    fun stop() {
        active = false
        generation++
        handler.removeCallbacks(poll)
        parked = null
        runCatching { vehicle?.release() }
        vehicle = null
        changed()
    }
}
