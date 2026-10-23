package com.kiki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupPickerTest {
    private val now = 100_000L

    @Test
    fun `picks most recent within 12h`() {
        val messages = listOf(NtfyMessage("a", 99_000, "a"), NtfyMessage("b", 99_500, "b"))
        assertEquals("b", StartupPicker.pick(messages, now, null)?.id)
    }

    @Test
    fun `ignores older than 12h`() =
        assertNull(StartupPicker.pick(listOf(NtfyMessage("old", now - 43_201, "x")), now, null))

    @Test
    fun `ignores already shown id`() {
        val messages = listOf(NtfyMessage("a", 99_000, "a"), NtfyMessage("b", 99_500, "b"))
        assertNull(StartupPicker.pick(messages, now, "b"))
    }

    @Test
    fun `empty list gives null`() = assertNull(StartupPicker.pick(emptyList(), now, null))
}
