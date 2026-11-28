package com.vlesscardvpn.core

object VpnRecoveryPolicy {
    const val RETRY_MS=1000L
    const val RESERVE_RETRY_MS=3000L
    /** Quiet screen may suspend optional work, never Auto's failure detector. */
    fun pauseWatchdog(quiet:Boolean,auto:Boolean)=quiet && !auto
    @Suppress("UNUSED_PARAMETER") fun healthOnly(quiet:Boolean,auto:Boolean)=auto
    /** 1.2.166: faster failure detection on mobile handoffs — the watchdog re-checks sooner
     *  (4 s floor, 10 s cap instead of 6 s / 15 s), so a route killed by a cell/Wi-Fi switch
     *  is retried earlier. Quiet screen keeps its calm 20 s cadence. */
    fun watchPeriod(requested: Long, auto: Boolean, quiet:Boolean=false) =
        if(auto && quiet) 20000L else if(auto) requested.coerceIn(4000L,10000L) else requested
}
