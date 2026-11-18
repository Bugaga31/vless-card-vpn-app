package com.vlesscardvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSpeedR256Test {
    @Test fun firstSampleIsBaselineOnly() {
        SessionMeter.reset()
        val s = SessionMeter.sample(1000, 500, 0)
        assertEquals(0L, s.down); assertEquals(0L, s.up)
        assertEquals(1, s.history.size)
    }
    @Test fun deltasBecomeBytesPerSecond() {
        SessionMeter.reset()
        SessionMeter.sample(0, 0, 0)
        val s = SessionMeter.sample(5000, 2000, 5000) // 5 s: 1000 B/s down, 400 B/s up
        assertEquals(1000L, s.down); assertEquals(400L, s.up)
    }
    @Test fun counterResetKeepsSpeedAtZero() {
        SessionMeter.reset()
        SessionMeter.sample(10_000, 10_000, 0)
        val s = SessionMeter.sample(5_000, 5_000, 5000) // TrafficStats reset: rx < lastRx
        assertEquals(0L, s.down); assertEquals(0L, s.up)
        assertEquals(2, s.history.size)
    }
    @Test fun historyIsCapped() {
        SessionMeter.reset()
        SessionMeter.sample(0, 0, 0)
        var rx = 0L
        repeat(SessionMeter.CAP + 10) { rx += 1000; SessionMeter.sample(rx, 0, (it + 1) * SessionMeter.SAMPLE_MS) }
        assertEquals(SessionMeter.CAP, SessionMeter.state.value.history.size)
    }
    @Test fun resetClearsHistory() {
        SessionMeter.reset(); SessionMeter.sample(0, 0, 0); SessionMeter.sample(5000, 0, 5000)
        SessionMeter.reset()
        assertTrue(SessionMeter.state.value.history.isEmpty())
        assertEquals(0L, SessionMeter.state.value.down)
    }
}
