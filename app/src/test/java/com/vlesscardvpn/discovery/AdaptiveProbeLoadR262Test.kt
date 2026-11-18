package com.vlesscardvpn.discovery

import com.vlesscardvpn.core.AdaptiveProbeLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R262: the cheap TCP prefilter must not backfire at high parallelism either.
 * Its budget grows with the slot count, and after a heavy-failure wave the
 * prefilter-failed candidates stay in the queue demoted instead of dropped. */
class AdaptiveProbeLoadR262Test {
 @Test fun prefilterBudgetGrowsWithSlots() {
     assertEquals(600L, AdaptiveProbeLoad.prefilterBudgetMs(1))
     assertTrue(AdaptiveProbeLoad.prefilterBudgetMs(10) > AdaptiveProbeLoad.prefilterBudgetMs(5))
     assertTrue(AdaptiveProbeLoad.prefilterBudgetMs(10) <= 600L + 2500L)
 }
 @Test fun saturatedWaveKeepsTcpFailures() {
     assertTrue(AdaptiveProbeLoad.keepTcpFailures(0.7))
     assertTrue(AdaptiveProbeLoad.keepTcpFailures(0.6))
     assertFalse(AdaptiveProbeLoad.keepTcpFailures(0.0))
 }
}
