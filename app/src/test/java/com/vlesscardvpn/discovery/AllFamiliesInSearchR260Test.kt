package com.vlesscardvpn.discovery

import com.vlesscardvpn.core.TransportExploration
import com.vlesscardvpn.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R260: the search puts every config family in work instead of two reservations.
 * All distinct protocol/network/security families get a representative in the first wave. */
class AllFamiliesInSearchR260Test {
 private fun node(id: Int, network: String = "tcp", security: String = "none") = Server(
     name = "n$id", protocol = "vless", address = "a$id.example", port = 443, secret = "x",
     network = network, security = security)
 @Test fun representativesCoverAllFamilies() {
     val catalog = listOf(node(1), node(2,"ws"), node(3,"grpc"), node(4,"http"), node(5,"quic"),
         node(6, security="tls"), node(7, network="xhttp"), node(8,"grpc","tls"), node(9,"grpc","tls"))
     val got = TransportExploration.representatives(catalog)
     assertEquals(catalog.distinctBy { listOf(it.protocol, it.network, it.security).joinToString("|") }.size, got.size)
 }
 @Test fun mergeFillsWaveWithOnePerFamily() {
     val base = (1..12).map { node(it) }
     val extra = listOf(node(20,"ws","tls"), node(21,"grpc"), node(22,"quic"), node(23,"xhttp","tls"))
     val got = TransportExploration.merge(base, base + extra, 20)
     assertEquals(base.first(), got.first())
     assertEquals(extra.size, got.drop(1).count { it in extra })
     assertTrue(got.size <= 20)
 }
}
