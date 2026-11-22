package com.vlesscardvpn.core

import org.junit.Assert.*
import org.junit.Test

/** 1.2.153: the TCP prefilter window follows the device plan, not the user's route-parallel ceiling. */
class ProbeConcurrencyTcpWindowR264Test {
    private fun plan(temp: Double = 0.0, profile: String = "balanced") =
        PowerPolicy.evaluate(PowerPolicy.Input(now = 1000L, temp = temp), PowerPolicy.Options(profile = profile))
    @Test fun foregroundStaysGentleBackgroundUsesThePlan() {
        assertEquals(10, ProbeConcurrencyPolicy.tcpWindow(32, plan(), foreground = true))
        assertEquals(32, ProbeConcurrencyPolicy.tcpWindow(32, plan(), foreground = false))
        assertEquals(64, ProbeConcurrencyPolicy.tcpWindow(96, plan(), foreground = false))
    }
    @Test fun hotPlanShrinksTheWindow() {
        assertEquals(2, ProbeConcurrencyPolicy.tcpWindow(32, plan(temp = 46.0), foreground = true))
        assertEquals(2, ProbeConcurrencyPolicy.tcpWindow(32, plan(temp = 46.0), foreground = false))
    }
    @Test fun ecoProfileBoundsBoth() {
        assertEquals(4, ProbeConcurrencyPolicy.tcpWindow(32, plan(profile = "eco"), foreground = true))
        assertEquals(8, ProbeConcurrencyPolicy.tcpWindow(32, plan(profile = "eco"), foreground = false))
    }
    @Test fun smallRequestsStaySmall() {
        assertEquals(1, ProbeConcurrencyPolicy.tcpWindow(1, plan(), foreground = true))
        assertEquals(1, ProbeConcurrencyPolicy.tcpWindow(0, plan(), foreground = true))
    }
}
