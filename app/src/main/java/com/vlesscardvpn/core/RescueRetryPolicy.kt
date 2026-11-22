package com.vlesscardvpn.core

/** Pacing for the reconnect loop after a failed rescue pass. Recovery starts fast: the first
 * three passes keep the old 1 s cadence. A long outage where nothing changed slows down to save
 * battery and traffic. New material resets to the fast pace at once: a refreshed catalog means
 * new configs worth probing, and a network change means a fresh environment for every host. */
object RescueRetryPolicy {
    const val FAST_MS = 1_000L
    const val CAP_MS = 15_000L
    val LADDER_MS = longArrayOf(1_000L, 1_000L, 1_000L, 2_000L, 3_000L, 5_000L, 8_000L, 15_000L)
    fun delayMs(attempt: Int, catalogChanged: Boolean, networkChanged: Boolean): Long {
        if (catalogChanged || networkChanged) return FAST_MS
        return LADDER_MS[attempt.coerceIn(0, LADDER_MS.lastIndex)]
    }
    /** Human status line: «повторный поиск через 3 с». */
    fun describe(ms: Long): String = if (ms >= 1000L) "${ms / 1000} с" else "$ms мс"
}

/** Deterministic rotation of the ranked first wave. Repeated rescue passes lead with different
 * candidates, so a settled outage eventually covers the whole catalog instead of re-testing the
 * same head forever. Wave 0 (ordinary searches) keeps the exact previous order. */
object WaveRotation {
    fun <T> rotated(list: List<T>, by: Int): List<T> {
        if (list.size < 2 || by == 0) return list
        val n = list.size
        val k = ((by % n) + n) % n
        return if (k == 0) list else list.drop(k) + list.take(k)
    }
}
