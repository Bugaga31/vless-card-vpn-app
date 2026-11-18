package com.vlesscardvpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** RAM-only energy history for the Watson screen: battery level, charging flag and how much
 * optional work the automatic power plan currently allows. No persistence, no network, no IDs. */
object EnergyMeter {
    const val CAP = 96
    data class Sample(val at: Long, val battery: Int, val charging: Boolean, val temp: Int, val optionalAllowed: Boolean)
    private val history = ArrayDeque<Sample>(CAP)
    private val _samples = MutableStateFlow<List<Sample>>(emptyList())
    val samples: StateFlow<List<Sample>> = _samples.asStateFlow()
    val current: Sample? get() = _samples.value.lastOrNull()
    /** [optionalAllowed]: the automatic power plan lets light optional work run right now. */
    fun add(battery: Int, charging: Boolean, temp: Int, optionalAllowed: Boolean) {
        if (battery !in 1..100) return
        val now = System.currentTimeMillis()
        val s = Sample(now, battery, charging, temp, optionalAllowed)
        synchronized(history) {
            val last = history.lastOrNull()
            if (last == null || now - last.at >= 30_000L) {
                history.addLast(s)
                while (history.size > CAP) history.removeFirst()
            } else history[history.size - 1] = s
        }
        _samples.value = synchronized(history) { history.toList() }
    }
}
