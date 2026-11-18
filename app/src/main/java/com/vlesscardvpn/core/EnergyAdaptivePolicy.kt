package com.vlesscardvpn.core

/** Battery-driven probe scaling, the same idea as AdaptiveProbeLoad but keyed to energy:
 * a low, discharging battery gets fewer simultaneous probes (each radio wakeup costs real
 * charge), charging or unknown batteries keep the user's chosen parallelism untouched. */
object EnergyAdaptivePolicy {
    fun scaleSlots(slots: Int, battery: Int, charging: Boolean): Int {
        val base = slots.coerceIn(1, 32)
        if (charging || battery !in 1..100) return base
        return when {
            battery <= 15 -> maxOf(1, base / 2)
            battery <= 30 -> maxOf(1, base * 3 / 4)
            else -> base
        }
    }
}
