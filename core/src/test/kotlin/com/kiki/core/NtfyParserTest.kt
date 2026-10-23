package com.kiki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyParserTest {
    @Test
    fun `message line is parsed`() = assertEquals(
        NtfyMessage("abc", 1700000000, "coucou"),
        NtfyParser.parse("""{"id":"abc","time":1700000000,"event":"message","topic":"t","message":"coucou"}"""),
    )

    @Test
    fun `open and keepalive are ignored`() {
        assertNull(NtfyParser.parse("""{"id":"x","time":1,"event":"open","topic":"t"}"""))
        assertNull(NtfyParser.parse("""{"id":"y","time":2,"event":"keepalive","topic":"t"}"""))
    }

    @Test
    fun `broken json is ignored`() = assertNull(NtfyParser.parse("{pas du json"))

    @Test
    fun `blank line is ignored`() = assertNull(NtfyParser.parse(""))
}
