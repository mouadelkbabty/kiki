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

    const val NAME_MAX = 20

    /** Trims a sender name and keeps its first [NAME_MAX] code points. Returns null when nothing is left. */
    fun name(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty()) return null
        if (name.codePointCount(0, name.length) <= NAME_MAX) return name
        return name.substring(0, name.offsetByCodePoints(0, NAME_MAX))
    }
}
