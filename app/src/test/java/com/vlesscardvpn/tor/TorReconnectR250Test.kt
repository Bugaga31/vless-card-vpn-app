package com.vlesscardvpn.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** R250: a dropped control socket must re-arm, not idle the rotator for the whole session. */
class TorReconnectR250Test {

    /** Control stub that breaks mid-session: every command fails while [fail] is set. */
    private class DropControl : TorControl("127.0.0.1", 1) {
        var fail = false
        var exitNodes = mutableListOf<String>()
        var connectedTimes = 0
        override fun connect(cookieHex: String) { connectedTimes++ }
        override fun signalNewNym() { if (fail) throw IOException("EOF from control port") }
        override fun setExitCountries(countries: List<String>) {
            if (fail) throw IOException("EOF from control port")
            exitNodes += countries.joinToString(",")
        }
        override fun close() {}
    }

    @Test
    fun `failed tick drops the connection and a reconnect restores rotation`() {
        var t = 1_000_000L
        val control = DropControl()
        val rotation = TorRotation(rotateEveryMs = 60_000, now = { t })
        val rotator = TorRotator(control, rotation, cookieHex = "01")
        assertTrue(rotator.connect())
        assertFalse(rotator.needsReconnect)
        rotation.markRotated(t)
        t += 61_000
        control.fail = true // daemon restarted, control socket dead
        assertFalse(rotator.tick(t))
        assertTrue(rotator.needsReconnect)
        assertTrue(rotator.lastErrorOr != null)
        control.fail = false // daemon back up
        assertTrue(rotator.connect()) // the loop re-arms on the next tick
        assertFalse(rotator.needsReconnect)
        t += 61_000 // connect re-seeds the cadence clock: the fresh Tor already has a circuit
        assertTrue(rotator.tick(t))
    }

    @Test
    fun `reconnect re-pins the exit countries`() {
        val control = DropControl()
        val rotation = TorRotation(rotateEveryMs = 60_000, now = { 1_000_000L })
        val rotator = TorRotator(control, rotation, countries = listOf("nl", "de"), cookieHex = "01")
        assertTrue(rotator.connect())
        assertEquals(1, control.connectedTimes)
        // reconnect: the same countries must be applied to the fresh control session
        assertTrue(rotator.connect())
        assertEquals(2, control.connectedTimes)
        assertEquals(listOf("nl,de", "nl,de"), control.exitNodes)
    }

    @Test
    fun `control stays up across quiet ticks`() {
        val control = DropControl()
        val rotation = TorRotation(rotateEveryMs = 600_000, now = { 0 })
        val rotator = TorRotator(control, rotation, cookieHex = "01")
        assertTrue(rotator.connect())
        assertFalse(rotator.tick(1_000L)) // not due
        assertFalse(rotator.needsReconnect)
        assertNull(rotator.lastErrorOr)
    }
}
