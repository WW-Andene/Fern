package com.andene.fern

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Battery and thermal checks (roadmap item 55): whether the device asks apps to do less right
 * now - battery saver is on, or it's running hot enough that the system will throttle it. While
 * [constrained], Fern does its background work less often (autosave batches longer, animation
 * playback is capped) so drawing itself stays responsive and the phone cools down.
 *
 * Observed from the system (power-save broadcasts; thermal status listener on API 29+); call
 * [start] and [stop] with the activity's lifecycle.
 */
class PowerMonitor(private val context: Context) {
    var constrained by mutableStateOf(false)
        private set

    private val power = context.getSystemService(PowerManager::class.java)
    private var powerSave = false
    private var thermalStatus = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }
    private val thermalListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { status ->
            thermalStatus = status
            update()
        }
    } else {
        null
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            power?.addThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
        }
        refresh()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            power?.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
        }
    }

    private fun refresh() {
        powerSave = power?.isPowerSaveMode == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thermalStatus = power?.currentThermalStatus ?: 0
        update()
    }

    private fun update() {
        constrained = isConstrained(powerSave, thermalStatus)
    }

    companion object {
        /** PowerManager.THERMAL_STATUS_SEVERE: the level at which the system itself starts throttling hard. */
        const val THERMAL_SEVERE = 3

        fun isConstrained(powerSave: Boolean, thermalStatus: Int): Boolean = powerSave || thermalStatus >= THERMAL_SEVERE

        /** Autosave debounce: one write per burst of actions, longer while [constrained]. */
        fun autosaveDelayMillis(constrained: Boolean): Long = if (constrained) 3000L else 500L

        /** Animation playback rate, capped while [constrained]. */
        fun playbackFps(requested: Int, constrained: Boolean): Int = if (constrained) minOf(requested, CONSTRAINED_FPS) else requested

        private const val CONSTRAINED_FPS = 12
    }
}
