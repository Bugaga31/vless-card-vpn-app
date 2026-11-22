package com.vlesscardvpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Автоматическое управление энергией («муха сама управляет»).
 * Сам выбирает экономный или умеренный профиль по заряду, температуре, экрану,
 * нагрузке и измеренному расходу батареи. «Быстрый» остаётся только ручным выбором.
 * Не меняет настройки, VPN и аварийное восстановление. */
object AutoEnergyPolicy {
    const val AUTO = "auto"

    private val _effective = MutableStateFlow("balanced")
    val effective: StateFlow<String> = _effective.asStateFlow()
    private val _reason = MutableStateFlow("Автоэнергия ещё не считала показания телефона")
    val reason: StateFlow<String> = _reason.asStateFlow()
    private val _drain = MutableStateFlow(-1.0)
    val drain: StateFlow<Double> = _drain.asStateFlow()

    /** One call per PhonePower refresh; the manual profile always wins. */
    fun resolve(profile: String, i: PowerPolicy.Input, drainPerHour: Double): String {
        val p = pick(profile, i, drainPerHour)
        _effective.value = p.profile
        _reason.value = p.reason
        _drain.value = drainPerHour
        return p.profile
    }

    data class Pick(val profile: String, val reason: String)

    /** Fail closed: unknown or missing sensor data keeps the moderate plan. */
    fun pick(profile: String, i: PowerPolicy.Input, drainPerHour: Double): Pick {
        if (profile != AUTO) return Pick(profile, "Автоэнергия выключена: профиль выбран вручную")
        if (i.thermal >= 3 || i.temp >= 45.0) return Pick("eco", "Автоэнергия: критический нагрев — минимальная необязательная нагрузка")
        if (i.thermal >= 2 || (i.temp > 0 && i.temp >= if (i.charging) 39.0 else 41.0)) return Pick("eco", "Автоэнергия: телефон нагревается — экономный профиль до остывания")
        if (i.charging) {
            if (i.battery in 1..20) return Pick("eco", "Автоэнергия: зарядка при низком заряде — экономный профиль")
            return Pick("balanced", "Автоэнергия: зарядка — умеренная нагрузка разрешена")
        }
        if (i.battery in 1..30) return Pick("eco", "Автоэнергия: заряд ${i.battery}% — экономный профиль")
        if (!i.screen) return Pick("eco", "Автоэнергия: экран выключен — экономный профиль")
        if (i.gaming || i.voiceCall || i.lowMemory || i.predictedPressure) return Pick("eco", "Автоэнергия: телефон под нагрузкой — экономный профиль")
        if (drainPerHour >= 8.0) return Pick("eco", "Автоэнергия: расход выше обычного — экономный профиль")
        if (drainPerHour >= 0) return Pick("balanced", "Автоэнергия: расход батареи в норме — умеренная нагрузка")
        return Pick("balanced", "Автоэнергия: данных о расходе мало — умеренная нагрузка")
    }

    /** Измеренный расход батареи, %/час, по отрезкам вне зарядки. */
    fun drainPerHour(samples: List<EnergyMeter.Sample>): Double {
        val spans = ArrayList<Pair<Long, Int>>(samples.size)
        for (s in samples) if (s.battery in 1..100 && !s.charging) spans.add(s.at to s.battery)
        if (spans.size < 2) return -1.0
        // The latest battery-only window wins: a plug/unplug between samples splits it.
        var best = -1.0
        var start = 0
        var i = 1
        while (i <= spans.size) {
            // A rise means the filtered-out charging window sat between the samples: break here.
            val contiguous = i < spans.size && spans[i].first - spans[i - 1].first <= 10 * 60_000L &&
                spans[i].second <= spans[i - 1].second
            if (!contiguous) {
                val first = spans[start]; val last = spans[i - 1]
                val spanMs = last.first - first.first
                val drop = first.second - last.second
                if (spanMs >= 5 * 60_000L && drop > 0) best = best.coerceAtLeast(drop * 3_600_000.0 / spanMs)
                start = i
            }
            i++
        }
        return best
    }

    /** Adaptive pacing from the measured drain: optional work slows before the battery suffers. */
    fun pace(drainPerHour: Double): Double = when {
        drainPerHour < 0 -> 1.0
        drainPerHour >= 12.0 -> 2.0
        drainPerHour >= 6.0 -> 1.5
        else -> 1.0
    }

    /** Effective presentation flag for screens that only need "is eco active now". */
    fun ecoNow(settingsProfile: String) = settingsProfile == "eco" || (settingsProfile == AUTO && _effective.value == "eco")
}
