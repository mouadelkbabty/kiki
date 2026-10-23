package com.kiki.core

object MessageText {
    const val MAX = 300

    /** Trims the text and caps it at [MAX] code points (299 + "…"). Returns null when nothing is left. */
    fun normalize(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val count = text.codePointCount(0, text.length)
        if (count <= MAX) return text
        val end = text.offsetByCodePoints(0, MAX - 1)
        return text.substring(0, end) + "…"
    }
}
