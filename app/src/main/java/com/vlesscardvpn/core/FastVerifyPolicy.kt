package com.vlesscardvpn.core

/**
 * The first verification after connect or reconnect: a route proven seconds ago needs a shorter
 * ping, not the full budget. A missed fast probe still falls back to the calm full-budget retry,
 * so this only cuts the waiting on routes that are almost certainly alive.
 */
object FastVerifyPolicy {
    const val RECENT_MS = 2 * 60_000L      // the server passed a full check within the last 2 minutes
    const val FRESH_BUDGET_MS = 3500L      // first probe on a fresh route
    const val FALLBACK_BUDGET_MS = 6500L   // unknown or stale route: the old full budget
    const val RETRY_MS = 1500L             // calm second try after a failed first probe

    /** [ageMs] = time since the route's last successful check; null = unknown or a stale result. */
    fun budget(ageMs: Long?): Long = if (ageMs != null && ageMs in 0..RECENT_MS) FRESH_BUDGET_MS else FALLBACK_BUDGET_MS
}
