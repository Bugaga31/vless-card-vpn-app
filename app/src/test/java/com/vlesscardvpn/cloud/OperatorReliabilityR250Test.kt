package com.vlesscardvpn

import com.vlesscardvpn.core.CloudSpeed
import com.vlesscardvpn.core.Report
import com.vlesscardvpn.model.Server
import org.junit.Assert.assertEquals
import org.junit.Test

/** Операторские замеры облака: тонкое доказательство не вытесняет GitHub-замер,
 *  а ненадёжный сервер проигрывает чуть более медленному, но стабильному. */
class OperatorReliabilityR250Test {
    private fun srv(i: Int) = Server(name = "s$i", protocol = "vless", address = "10.0.0.$i", port = 443, secret = "u")

    @Test fun thinOperatorEvidenceDoesNotDisplaceTheGithubMeasurement() {
        val a = srv(1)
        val n = CloudSpeed.parseNet("""{"${Report.serverKey(a)}":[7000,100,2]}""")
        // two lucky checks do not yet describe this operator
        assertEquals(50000, CloudSpeed.speedOf(Report.serverKey(a), mapOf(Report.serverKey(a) to 50000), n))
        // three checks at 100% do
        val ok = CloudSpeed.parseNet("""{"${Report.serverKey(a)}":[7000,100,3]}""")
        assertEquals(7000, CloudSpeed.speedOf(Report.serverKey(a), mapOf(Report.serverKey(a) to 50000), ok))
    }

    @Test fun pickPrefersAReliableServerOverAMarginallyFasterFlakyOne() {
        val reliable = srv(1); val flaky = srv(2); val slow = srv(3)
        val speeds = mapOf(Report.serverKey(reliable) to 5000, Report.serverKey(flaky) to 5100, Report.serverKey(slow) to 2000)
        val here = CloudSpeed.parseNet(
            """{"${Report.serverKey(reliable)}":[5000,95,10],"${Report.serverKey(flaky)}":[5100,40,10]}""")
        val got = CloudSpeed.pick(listOf(reliable, flaky, slow), emptyList(), speeds = speeds, here = here)
        assertEquals(listOf(reliable, flaky), got)
    }

    @Test fun unknownOperatorEvidenceKeepsTheRawSpeedOrder() {
        val fast = srv(1); val slow = srv(2)
        val speeds = mapOf(Report.serverKey(fast) to 9000, Report.serverKey(slow) to 4000)
        assertEquals(listOf(fast, slow), CloudSpeed.pick(listOf(fast, slow), emptyList(), speeds = speeds))
    }

    @Test fun reliabilityIsBetaSmoothedLikeSuccessChance() {
        val k = "abcd1234"
        assertEquals(1.0, CloudSpeed.reliability(k, emptyMap()), 1e-9)
        assertEquals((0.9 * 4 + 2) / 8, CloudSpeed.reliability(k, mapOf(k to intArrayOf(8000, 90, 4))), 1e-9)
        // fewer than three checks: no reliability opinion, not a penalty
        assertEquals(1.0, CloudSpeed.reliability(k, mapOf(k to intArrayOf(8000, 90, 2))), 1e-9)
    }
}
