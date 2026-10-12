package com.andene.fern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerMonitorTest {
    @Test
    fun batterySaverOrSevereHeatConstrainsWork() {
        assertFalse(PowerMonitor.isConstrained(powerSave = false, thermalStatus = 2))
        assertTrue(PowerMonitor.isConstrained(powerSave = true, thermalStatus = 0))
        assertTrue(PowerMonitor.isConstrained(powerSave = false, thermalStatus = PowerMonitor.THERMAL_SEVERE))
    }

    @Test
    fun constrainedDevicesSaveLessOftenAndPlayBackSlower() {
        assertEquals(500L, PowerMonitor.autosaveDelayMillis(false))
        assertEquals(3000L, PowerMonitor.autosaveDelayMillis(true))
        assertEquals(24, PowerMonitor.playbackFps(24, false))
        assertEquals(12, PowerMonitor.playbackFps(24, true))
        assertEquals(8, PowerMonitor.playbackFps(8, true))
    }
}
