package com.vlesscardvpn.discovery

import com.vlesscardvpn.core.ConnectPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

/** R259: the first wave no longer skips the 600 ms TCP prefilter; dead hosts
 * must die cheaply instead of burning a full HTTPS probe budget in their slot. */
class FirstWaveTcpFilterR259Test {
 @Test fun firstWaveRunsTcpPrefilter() {assertTrue(ConnectPolicy.requiresTcpFilter(true))}
 @Test fun backupWavesKeepTcpPrefilter() {assertTrue(ConnectPolicy.requiresTcpFilter(false))}
}
