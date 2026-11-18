package com.vlesscardvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReporterR257Test {
    private fun d(now: Long, lastSentAt: Long = 0, lastDigest: Int = 0, lastDigestAt: Long = 0,
                  sentToday: Int = 0, today: String = "2026-11-18", sentDay: String = "", digest: Int = 7) =
        BugReporter.decide("connect", digest, lastSentAt, lastDigest, lastDigestAt, sentToday, today, sentDay, now)

    @Test fun firstReportGoesOut() { assertTrue(d(now = 1_000).send) }
    @Test fun cooldownHoldsRepeat() { assertFalse(d(now = 1_000 + BugReporter.MIN_GAP_MS - 1, lastSentAt = 1_000).send) }
    @Test fun sendAgainAfterGap() { assertTrue(d(now = 1_000 + BugReporter.MIN_GAP_MS + 1, lastSentAt = 1_000).send) }
    @Test fun sameProblemWithinDedupeWindowIsOneReport() {
        assertFalse(d(now = 1_000 + BugReporter.MIN_GAP_MS + 1, lastSentAt = 1_000, lastDigest = 7, lastDigestAt = 2).send)
    }
    @Test fun changedSignatureSendsAgain() {
        assertTrue(d(now = 1_000 + BugReporter.MIN_GAP_MS + 1, lastSentAt = 1_000, lastDigest = 8, lastDigestAt = 2).send)
    }
    @Test fun oldDuplicateSendsAgain() {
        assertTrue(d(now = BugReporter.DEDUPE_MS + 1, lastSentAt = 0, lastDigest = 7, lastDigestAt = 1).send)
    }
    @Test fun dailyCapStopsFlood() {
        assertFalse(d(now = 10 * BugReporter.MIN_GAP_MS, lastSentAt = 0, sentToday = BugReporter.MAX_PER_DAY, sentDay = "2026-11-18").send)
    }
    @Test fun newDayResetsTheCap() {
        assertTrue(d(now = 10 * BugReporter.MIN_GAP_MS, lastSentAt = 0, sentToday = BugReporter.MAX_PER_DAY, sentDay = "2026-11-17").send)
    }
}
