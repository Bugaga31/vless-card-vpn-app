package com.vlesscardvpn.discovery

import com.vlesscardvpn.core.AdaptiveProbeLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R261: a higher probe-parallelism setting must stop backfiring.
 * The per-probe budget grows with concurrency, and a heavy-failure wave
 * throttles the next wave's slots so the phone link stops saturating. */
class AdaptiveProbeLoadR261Test {
 @Test fun budgetGrowsWithConcurrency() {
     assertEquals(4500L, AdaptiveProbeLoad.budgetFor(4500L, 1))
     assertTrue(AdaptiveProbeLoad.budgetFor(4500L, 10) > AdaptiveProbeLoad.budgetFor(4500L, 5))
     assertTrue(AdaptiveProbeLoad.budgetFor(4500L, 10) <= 4500L + 2500L)
 }
 @Test fun heavyFailureWaveThrottlesNextWave() {
     AdaptiveProbeLoad.lastFailureRatio = 0.0
     AdaptiveProbeLoad.record(20, 0)
     assertEquals(1.0, AdaptiveProbeLoad.lastFailureRatio, 1e-9)
     assertEquals(5, AdaptiveProbeLoad.slotsFor(10, AdaptiveProbeLoad.lastFailureRatio))
     assertEquals(1, AdaptiveProbeLoad.slotsFor(1, 1.0))
     assertEquals(2, AdaptiveProbeLoad.slotsFor(3, 0.6))
     assertEquals(10, AdaptiveProbeLoad.slotsFor(10, 0.59))
 }
 @Test fun emptyWaveChangesNothing() {
     AdaptiveProbeLoad.lastFailureRatio = 0.0
     AdaptiveProbeLoad.record(0, 0)
     assertEquals(0.0, AdaptiveProbeLoad.lastFailureRatio, 1e-9)
 }
}
