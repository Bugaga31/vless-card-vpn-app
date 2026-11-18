package com.vlesscardvpn.core

/**
 * Rate limits of automatic bug reports: many failures are usually one problem, not many.
 * The signature (kind + digest of the message) dedupes the same failure; the daily cap and
 * the minimum gap keep one buggy build from flooding the cloud intake.
 */
object BugReporter {
    const val MIN_GAP_MS = 20 * 60_000L        // not more often than every 20 minutes
    const val MAX_PER_DAY = 4
    const val DEDUPE_MS = 12 * 3600_000L       // the same problem within 12 h is one report

    data class Decision(val send: Boolean, val reason: String)

    /** [lastSentAt] counts successful sends; a failed upload retries on the next trigger. */
    fun decide(kind: String, digest: Int, lastSentAt: Long, lastDigest: Int, lastDigestAt: Long,
               sentToday: Int, today: String, sentDay: String, now: Long): Decision = when {
        // 0 means "never sent/recorded", not "long ago".
        lastSentAt > 0 && now - lastSentAt < MIN_GAP_MS -> Decision(false, "пауза между отчётами")
        sentDay == today && sentToday >= MAX_PER_DAY -> Decision(false, "дневной лимит")
        lastDigest != 0 && digest == lastDigest && now - lastDigestAt < DEDUPE_MS -> Decision(false, "такой сбой уже отправлен")
        else -> Decision(true, "ok")
    }
}
