package com.vlesscardvpn.discovery

import com.vlesscardvpn.core.EnergyAdaptivePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/** R263: battery-adaptive probe scaling. */
class EnergyAdaptivePolicyR263Test {
 @Test fun lowDischargingBatteryShrinksSlots() {
     assertEquals(10, EnergyAdaptivePolicy.scaleSlots(10, 50, false))
     assertEquals(7, EnergyAdaptivePolicy.scaleSlots(10, 25, false))
     assertEquals(5, EnergyAdaptivePolicy.scaleSlots(10, 10, false))
     assertEquals(1, EnergyAdaptivePolicy.scaleSlots(1, 10, false))
 }
 @Test fun chargingOrUnknownBatteryKeepsSlots() {
     assertEquals(10, EnergyAdaptivePolicy.scaleSlots(10, 10, true))
     assertEquals(10, EnergyAdaptivePolicy.scaleSlots(10, -1, false))
 }
}
