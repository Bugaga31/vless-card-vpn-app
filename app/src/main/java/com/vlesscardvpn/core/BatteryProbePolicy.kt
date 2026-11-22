package com.vlesscardvpn.core

/** Native multi-route observatory cadence; no wakelock and no disconnect on low battery. */
object BatteryProbePolicy {
    fun interval(profile: String) = when (profile) { "auto" -> interval(AutoEnergyPolicy.effective.value); "eco" -> "90s"; "fast" -> "20s"; else -> "45s" }
}
