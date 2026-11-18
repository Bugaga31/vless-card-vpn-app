package com.vlesscardvpn.core

/** The phone's own link is the bottleneck of a search wave: N simultaneous TLS probes share one
 * uplink, each handshake stretches, and a fixed per-probe budget starts expiring on healthy
 * servers. The user sees exactly that: a higher "проверок" setting slows the search down and
 * filters out more servers. Two counter-measures, both conservative:
 * 1) the per-probe budget grows with the number of simultaneous probes (they share bandwidth);
 * 2) after a wave where most probes failed, the next wave runs with fewer slots, so the link
 *    stops saturating and healthy routes stop being marked dead.
 * Acceptance gates are untouched: this only changes how long and how widely we listen. */
object AdaptiveProbeLoad {
    @Volatile var lastFailureRatio: Double = 0.0

    fun budgetFor(baseMs: Long, slots: Int): Long {
        val extra = 250L * (slots - 1).coerceIn(0, 10)
        return baseMs + extra
    }

    fun slotsFor(requested: Int, failureRatio: Double): Int {
        val base = requested.coerceIn(1, 32)
        // Throttling only ever cuts slots; the safety floor of 2 never raises
        // a request that asked for fewer.
        return if (failureRatio >= 0.6) maxOf(minOf(2, base), base / 2) else base
    }

    /** Call after a wave that ran to completion; an early-stopped wave proves nothing about load. */
    fun record(total: Int, passed: Int) {
        if (total > 0) lastFailureRatio = (total - passed).toDouble() / total
    }
}
