package com.kiki.core

/** Reconnection delays: doubles from [initialMs] up to [maxMs]; [reset] after a successful connection. */
class Backoff(private val initialMs: Long = 2_000, private val maxMs: Long = 300_000) {
    private var current = initialMs

    fun next(): Long {
        val delay = current
        current = minOf(current * 2, maxMs)
        return delay
    }

    fun reset() {
        current = initialMs
    }
}
