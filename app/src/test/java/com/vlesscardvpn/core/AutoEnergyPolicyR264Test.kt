package com.vlesscardvpn.core

import com.vlesscardvpn.model.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEnergyPolicyR264Test {
    private fun sample(at: Long, battery: Int, charging: Boolean = false, temp: Double = 0.0) =
        EnergyMeter.Sample(at, battery, charging, temp, true)

    @Test
    fun `manual profile passes through untouched`() {
        assertEquals("eco", AutoEnergyPolicy.pick("eco", PowerPolicy.Input(0), -1.0).profile)
        assertEquals("fast", AutoEnergyPolicy.pick("fast", PowerPolicy.Input(0), 30.0).profile)
        assertEquals("balanced", AutoEnergyPolicy.pick("balanced", PowerPolicy.Input(0), 30.0).profile)
    }

    @Test
    fun `auto never selects fast`() {
        for (battery in intArrayOf(-1, 1, 10, 25, 40, 70, 100))
            for (thermal in intArrayOf(-1, 1, 2, 3))
                for (screen in booleanArrayOf(true, false))
                    for (charging in booleanArrayOf(true, false))
                        for (drain in doubleArrayOf(-1.0, 0.5, 5.9, 8.0, 30.0)) {
                            val p = AutoEnergyPolicy.pick("auto",
                                PowerPolicy.Input(0, battery = battery, charging = charging, screen = screen, thermal = thermal), drain).profile
                            assertTrue("got $p", p == "eco" || p == "balanced")
                        }
    }

    @Test
    fun `critical heat forces eco`() {
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, thermal = 3), -1.0).profile)
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, temp = 46.0), -1.0).profile)
    }

    @Test
    fun `charging allows moderate work unless the battery is low`() {
        assertEquals("balanced", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 50, charging = true), -1.0).profile)
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 15, charging = true), -1.0).profile)
    }

    @Test
    fun `low battery screen off and pressure choose eco`() {
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 25), -1.0).profile)
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 80, screen = false), -1.0).profile)
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 80, gaming = true), -1.0).profile)
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 80, voiceCall = true), -1.0).profile)
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 80, predictedPressure = true), -1.0).profile)
    }

    @Test
    fun `high measured drain steps down`() {
        assertEquals("eco", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 80), 9.0).profile)
        assertEquals("balanced", AutoEnergyPolicy.pick("auto", PowerPolicy.Input(0, battery = 80), 3.0).profile)
    }

    @Test
    fun `drain needs a real battery-only window`() {
        assertEquals(-1.0, AutoEnergyPolicy.drainPerHour(emptyList()), 0.0)
        // One minute of sampling is not a drain measurement.
        assertEquals(-1.0, AutoEnergyPolicy.drainPerHour(listOf(sample(0, 80), sample(60_000, 79))), 0.0)
        // 5% over 5 minutes -> 60%/h.
        assertEquals(60.0, AutoEnergyPolicy.drainPerHour(
            listOf(sample(0, 80), sample(60_000, 79), sample(120_000, 78), sample(180_000, 77),
                sample(240_000, 76), sample(300_000, 75))), 0.1)
        // Charging samples never count as drain.
        assertEquals(-1.0, AutoEnergyPolicy.drainPerHour(
            listOf(sample(0, 80, charging = true), sample(300_000, 79, charging = true))), 0.0)
        // A charge in the middle splits windows: the latest battery-only window wins.
        val s = listOf(sample(0, 80), sample(60_000, 79), sample(120_000, 78), sample(180_000, 77),
            sample(240_000, 76), sample(400_000, 80, charging = true),
            sample(500_000, 88), sample(600_000, 87), sample(700_000, 86), sample(800_000, 85))
        assertEquals(36.0, AutoEnergyPolicy.drainPerHour(s), 0.1)
    }

    @Test
    fun `pacing only slows when the drain is real and high`() {
        assertEquals(1.0, AutoEnergyPolicy.pace(-1.0), 0.0)
        assertEquals(1.0, AutoEnergyPolicy.pace(3.0), 0.0)
        assertEquals(1.5, AutoEnergyPolicy.pace(6.0), 0.0)
        assertEquals(2.0, AutoEnergyPolicy.pace(12.0), 0.0)
    }

    @Test
    fun `settings accept auto and keep the balanced default`() {
        assertEquals("auto", Settings.fromJson(JSONObject().put("powerProfile", "auto")).powerProfile)
        assertEquals("balanced", Settings.fromJson(JSONObject().put("powerProfile", "fastest")).powerProfile)
        assertEquals("balanced", Settings().powerProfile)
    }

    @Test
    fun `ecoNow follows the effective profile only in auto`() {
        AutoEnergyPolicy.resolve("auto", PowerPolicy.Input(0, battery = 25), -1.0)
        assertTrue(AutoEnergyPolicy.ecoNow("auto"))
        AutoEnergyPolicy.resolve("balanced", PowerPolicy.Input(0, battery = 25), -1.0)
        assertFalse(AutoEnergyPolicy.ecoNow("auto"))
        assertTrue(AutoEnergyPolicy.ecoNow("eco"))
    }
}
