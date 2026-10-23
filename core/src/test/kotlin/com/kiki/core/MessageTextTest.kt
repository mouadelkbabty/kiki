package com.kiki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTextTest {
    @Test
    fun `trims spaces`() = assertEquals("salut", MessageText.normalize("  salut \n"))

    @Test
    fun `blank becomes null`() = assertNull(MessageText.normalize("   \n "))

    @Test
    fun `300 chars kept as is`() = assertEquals("a".repeat(300), MessageText.normalize("a".repeat(300)))

    @Test
    fun `long text cut to 299 plus ellipsis`() {
        val out = MessageText.normalize("a".repeat(400))!!
        assertEquals(300, out.codePointCount(0, out.length))
        assertTrue(out.endsWith("…"))
    }

    @Test
    fun `emoji never split`() {
        val out = MessageText.normalize("😀".repeat(350))!!
        assertEquals(300, out.codePointCount(0, out.length))
        assertFalse(out.contains('�'))
        assertTrue(out.dropLast(1).codePoints().allMatch { it == "😀".codePointAt(0) })
    }
}
