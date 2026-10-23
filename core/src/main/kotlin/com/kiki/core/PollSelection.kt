package com.kiki.core

/** Which poll answer is highlighted; ← and → move it without wrapping. */
class PollSelection(private val count: Int) {
    var index = 0
        private set

    fun left() {
        if (index > 0) index--
    }

    fun right() {
        if (index < count - 1) index++
    }
}
