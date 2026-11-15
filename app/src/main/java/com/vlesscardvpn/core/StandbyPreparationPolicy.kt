package com.vlesscardvpn.core

/** Reliability work, separate from optional speed/phone optimization. Never applies a route. */
object StandbyPreparationPolicy {
    const val TARGET = 3
    const val POLL_MS = 5_000L
    const val ATTEMPT_INTERVAL_MS = 30_000L
    const val PASS_TIMEOUT_MS = 45_000L
    const val BASE_POOL = 6
    const val EXTRA_CANDIDATE_MS = 8_000L
    const val MAX_PASS_TIMEOUT_MS = 90_000L
    /** One pass must still fit its wall time when the candidate pool is larger than the old six-slot wave. */
    fun passTimeoutMs(candidates: Int): Long =
        (PASS_TIMEOUT_MS + EXTRA_CANDIDATE_MS * (candidates - BASE_POOL).coerceAtLeast(0)).coerceAtMost(MAX_PASS_TIMEOUT_MS)
    fun mayPrepare(auto: Boolean, plan: PowerPolicy.Plan) = auto && !plan.memory.hot && !plan.voiceCallActive
    /** Continue only when a new fully verified reserve was actually recorded. */
    fun continueAfter(before: Int, after: Int) = before >= 0 && after > before && after < TARGET
    fun due(now: Long, lastAttempt: Long?, warm: Int, busy: Boolean): Boolean =
        warm < TARGET && !busy && (lastAttempt == null || now < lastAttempt || now - lastAttempt >= ATTEMPT_INTERVAL_MS)
}
