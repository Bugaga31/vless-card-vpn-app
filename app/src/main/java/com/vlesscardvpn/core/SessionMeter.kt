package com.vlesscardvpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * «Скорость за сессию»: живая история скорости VPN (байт/с), которую сервис считает раз в 5 секунд,
 * пока подключение активно. График на главном экране живёт на этих данных: он уже заполнен, когда
 * экран открывают, даже если тот долго был закрыт. Сбросившиеся системные счётчики (rx меньше прошлого
 * замера, например после перезагрузки) считаются нулевой скоростью, а не всплеском.
 */
object SessionMeter {
    /** 96 замеров × 5 с ≈ 8 минут истории. */
    const val CAP = 96
    const val SAMPLE_MS = 5_000L

    /** Скорость в байтах в секунду. */
    data class Sample(val down: Long, val up: Long)
    data class Snapshot(val down: Long, val up: Long, val history: List<Sample>)

    private val _state = MutableStateFlow(Snapshot(0, 0, emptyList()))
    val state: StateFlow<Snapshot> = _state

    @Volatile private var lastRx = -1L
    @Volatile private var lastTx = -1L
    @Volatile private var lastAt = 0L

    /** Новая сессия: история сбрасывается, следующий замер задаёт базовую линию. */
    @Synchronized fun reset() { lastRx = -1; lastTx = -1; lastAt = 0; _state.value = Snapshot(0, 0, emptyList()) }

    /** Абсолютные счётчики процесса (TrafficStats): дельта между замерами = скорость. */
    @Synchronized fun sample(rx: Long, tx: Long, nowMs: Long): Snapshot {
        val dt = (nowMs - lastAt).coerceAtLeast(1)
        val down = if (lastRx < 0 || rx < lastRx) 0L else speed(rx - lastRx, dt)
        val up = if (lastTx < 0 || tx < lastTx) 0L else speed(tx - lastTx, dt)
        lastRx = rx; lastTx = tx; lastAt = nowMs
        val s = Snapshot(down, up, (_state.value.history + Sample(down, up)).takeLast(CAP))
        _state.value = s
        return s
    }

    /** Байты за [dtMs] миллисекунд → байты в секунду. */
    private fun speed(bytes: Long, dtMs: Long): Long = bytes * 1000 / dtMs
}
