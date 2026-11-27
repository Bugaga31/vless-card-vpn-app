package com.vlesscardvpn.core

/** User ceiling for simultaneous server routes, not a count of individual HTTP requests.
 * The explicit user ceiling is not reduced by app power/call policies. Android still owns system thermal safety.
 */
object ProbeConcurrencyPolicy {
    const val DEFAULT = 40
    val choices = listOf(1, 3, 5, 10, 20, 40)
    fun normalize(value:Int):Int = value.takeIf {it in choices} ?: DEFAULT
    @Suppress("UNUSED_PARAMETER")
    fun limit(requested:Int,selected:Int,plan:PowerPolicy.Plan,foreground:Boolean):Int =
        minOf(requested.coerceAtLeast(1),normalize(selected))
    fun batch(requested:Int,slots:Int):Int = minOf(requested.coerceAtLeast(1),slots.coerceAtLeast(1))
    /** A manual bulk check of many servers may scale past the selected ceiling: more targets, more slots (up to 20). */
    fun bulkSlots(selected:Int,count:Int):Int =
        if (count >= 16) maxOf(normalize(selected), minOf(40, count / 6)) else normalize(selected)
/** The cheap TCP prefilter opens raw sockets, not server routes: it is bounded by the device
 * plan (PowerPolicy tcpParallel / foregroundTcpParallel), never by the user's route-parallel
 * ceiling. Foreground stays gentler for UI smoothness; the plan's hot/eco/pressure tiers still win. */
    fun tcpWindow(requested:Int,plan:PowerPolicy.Plan,foreground:Boolean):Int =
        minOf(requested.coerceAtLeast(1),(if (foreground) plan.foregroundTcpParallel else plan.tcpParallel).coerceAtLeast(1))
}
