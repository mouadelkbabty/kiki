package com.kiki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {
    @Test
    fun `doubles then caps`() {
        val b = Backoff()
        assertEquals(
            listOf(2000L, 4000, 8000, 16000, 32000, 64000, 128000, 256000, 300000, 300000),
            List(10) { b.next() },
        )
    }

    @Test
    fun `reset restarts at 2s`() {
        val b = Backoff()
        repeat(5) { b.next() }
        b.reset()
        assertEquals(2000L, b.next())
    }
}
