package com.vlesscardvpn.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FastVerifyPolicyR258Test {
    @Test fun justVerifiedRouteGetsTheShortPing() {
        assertEquals(FastVerifyPolicy.FRESH_BUDGET_MS, FastVerifyPolicy.budget(0L))
        assertEquals(FastVerifyPolicy.FRESH_BUDGET_MS, FastVerifyPolicy.budget(FastVerifyPolicy.RECENT_MS))
    }
    @Test fun staleOrUnknownRouteKeepsTheFullBudget() {
        assertEquals(FastVerifyPolicy.FALLBACK_BUDGET_MS, FastVerifyPolicy.budget(FastVerifyPolicy.RECENT_MS + 1))
        assertEquals(FastVerifyPolicy.FALLBACK_BUDGET_MS, FastVerifyPolicy.budget(null))
        assertEquals(FastVerifyPolicy.FALLBACK_BUDGET_MS, FastVerifyPolicy.budget(-1L))
    }
    @Test fun retryIsCalmButNotSlow() { assertEquals(1500L, FastVerifyPolicy.RETRY_MS) }
}
