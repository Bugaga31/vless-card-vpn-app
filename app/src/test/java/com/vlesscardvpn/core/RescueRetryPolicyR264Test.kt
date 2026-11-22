package com.vlesscardvpn.core

import org.junit.Assert.*
import org.junit.Test

/** 1.2.153: reconnect pacing with a fast start, a settled-outage backoff and resets on new material. */
class RescueRetryPolicyR264Test {
    @Test fun ladderStartsFastAndCaps() {
        assertEquals(1000L, RescueRetryPolicy.delayMs(0, false, false))
        assertEquals(1000L, RescueRetryPolicy.delayMs(1, false, false))
        assertEquals(1000L, RescueRetryPolicy.delayMs(2, false, false))
        assertEquals(2000L, RescueRetryPolicy.delayMs(3, false, false))
        assertEquals(3000L, RescueRetryPolicy.delayMs(4, false, false))
        assertEquals(5000L, RescueRetryPolicy.delayMs(5, false, false))
        assertEquals(8000L, RescueRetryPolicy.delayMs(6, false, false))
        assertEquals(15000L, RescueRetryPolicy.delayMs(7, false, false))
        assertEquals(15000L, RescueRetryPolicy.delayMs(40, false, false))
    }
    @Test fun freshCatalogOrNewNetworkResetsToFast() {
        assertEquals(1000L, RescueRetryPolicy.delayMs(7, true, false))
        assertEquals(1000L, RescueRetryPolicy.delayMs(3, false, true))
        assertEquals(1000L, RescueRetryPolicy.delayMs(9, true, true))
    }
    @Test fun negativeAttemptIsClamped() {
        assertEquals(1000L, RescueRetryPolicy.delayMs(-1, false, false))
    }
    @Test fun describeSpeaksSeconds() {
        assertEquals("1 с", RescueRetryPolicy.describe(1000L))
        assertEquals("15 с", RescueRetryPolicy.describe(15000L))
    }
    @Test fun rotationPreservesElementsAndShiftsStart() {
        val l = listOf(1, 2, 3, 4, 5)
        assertEquals(l, WaveRotation.rotated(l, 0))
        assertEquals(listOf(3, 4, 5, 1, 2), WaveRotation.rotated(l, 2))
        assertEquals(l, WaveRotation.rotated(l, 5))
        assertEquals(listOf(3, 4, 5, 1, 2), WaveRotation.rotated(l, 7))
        assertEquals(listOf(2, 3, 4, 5, 1), WaveRotation.rotated(l, 11))
        assertEquals(listOf(1), WaveRotation.rotated(listOf(1), 3))
        assertTrue(WaveRotation.rotated(emptyList<Int>(), 4).isEmpty())
    }
}
