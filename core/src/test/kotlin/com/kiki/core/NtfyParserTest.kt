package com.kiki.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyParserTest {
    private val key = SecretCode.key("Abcdefghijklmnopqrstuvwxyz012345")

    private fun sealed(id: String, envelope: String) =
        JSONObject().put("id", id).put("time", 1700000000).put("event", "message")
            .put("message", Cipher.sealText(key, envelope)).toString()

    @Test
    fun `message line is parsed`() =
        assertEquals(NtfyMessage("abc", 1700000000, "coucou"), NtfyParser.parse(sealed("abc", Envelope.text("coucou")), key))

    @Test
    fun `open and keepalive are ignored`() {
        assertNull(NtfyParser.parse("""{"id":"x","time":1,"event":"open","topic":"t"}""", key))
        assertNull(NtfyParser.parse("""{"id":"y","time":2,"event":"keepalive","topic":"t"}""", key))
    }

    @Test
    fun `broken json is ignored`() = assertNull(NtfyParser.parse("{pas du json", key))

    @Test
    fun `blank line is ignored`() = assertNull(NtfyParser.parse("", key))

    @Test
    fun `blank text without sticker is ignored`() = assertNull(NtfyParser.parse(sealed("z", Envelope.text("   ")), key))

    @Test
    fun `long text is cut to 300`() {
        val text = NtfyParser.parse(sealed("l", Envelope.text("a".repeat(400))), key)!!.text!!
        assertEquals(300, text.codePointCount(0, text.length))
    }

    @Test
    fun `ping is not shown`() = assertNull(NtfyParser.parse(sealed("p", Envelope.ping()), key))

    @Test
    fun `poll with one choice is refused`() =
        assertNull(NtfyParser.parse(sealed("q", Envelope.poll("?", listOf("seul"))), key))
}
