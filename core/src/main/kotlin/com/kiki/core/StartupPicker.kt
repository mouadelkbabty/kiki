package com.kiki.core

object StartupPicker {
    private const val MAX_AGE_SEC = 43_200L

    /** The newest message younger than 12 h, unless it was already shown. */
    fun pick(messages: List<NtfyMessage>, nowSec: Long, lastShownId: String?): NtfyMessage? {
        val latest = messages.maxByOrNull { it.time } ?: return null
        if (nowSec - latest.time > MAX_AGE_SEC) return null
        if (latest.id == lastShownId) return null
        return latest
    }
}
