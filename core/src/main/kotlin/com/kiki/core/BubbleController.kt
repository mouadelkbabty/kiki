package com.kiki.core

fun interface Cancellable {
    fun cancel()
}

interface Scheduler {
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable
}

interface BubbleDisplay {
    fun show(message: NtfyMessage)
    fun hide()
}

/**
 * Shows one bubble at a time: a new message replaces the current one and restarts the timeout;
 * a remote key or the timeout hides it.
 */
class BubbleController(
    private val display: BubbleDisplay,
    private val scheduler: Scheduler,
    private val timeoutMs: Long = 300_000,
) {
    private var timer: Cancellable? = null
    private var current: NtfyMessage? = null

    var isShowing = false
        private set

    fun onMessage(m: NtfyMessage) {
        timer?.cancel()
        display.show(m)
        current = m
        isShowing = true
        lateinit var mine: Cancellable
        mine = scheduler.schedule(timeoutMs) {
            // A cancelled timer that still fires must not hide a newer bubble.
            if (timer === mine) hideNow()
        }
        timer = mine
    }

    /** Hides the bubble; returns the message the user dismissed (null if nothing was shown). */
    fun onUserKey(): NtfyMessage? {
        if (!isShowing) return null
        val dismissed = current
        hideNow()
        return dismissed
    }

    private fun hideNow() {
        timer?.cancel()
        timer = null
        current = null
        isShowing = false
        display.hide()
    }
}
