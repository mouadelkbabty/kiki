package com.kiki.core

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretCodeTest {
    private val code = "Abcdefghijklmnopqrstuvwxyz012345"

    @Test
    fun `topic is 40 hex chars and stable`() {
        val topic = SecretCode.topic(code)
        assertTrue(Regex("^[0-9a-f]{40}$").matches(topic))
        assertEquals(topic, SecretCode.topic(code))
    }

    @Test
    fun `topic never reveals the key`() =
        assertNotEquals(SecretCode.topic(code), SecretCode.key(code).joinToString("") { "%02x".format(it) }.take(40))

    @Test
    fun `known derivation matches the web page`() =
        // sha256("kiki-topic:" + code) — the page computes the same with WebCrypto.
        assertEquals("3a7932c5a1062c2e3bc260b54243bc65489c1f18", SecretCode.topic(code))

    @Test
    fun `code format is 32 letters or digits`() {
        assertTrue(SecretCode.isValid(code))
        assertTrue(!SecretCode.isValid("short"))
    }
}

class CipherTest {
    private val key = SecretCode.key("Abcdefghijklmnopqrstuvwxyz012345")
    private val otherKey = SecretCode.key("Zbcdefghijklmnopqrstuvwxyz012345")

    @Test
    fun `round trip`() {
        val sealed = Cipher.seal(key, "salut ❤️".toByteArray())
        assertArrayEquals("salut ❤️".toByteArray(), Cipher.open(key, sealed))
    }

    @Test
    fun `sealed text never contains the plaintext`() {
        val sealed = Cipher.seal(key, "ana jay".toByteArray())
        assertTrue(!String(sealed, Charsets.ISO_8859_1).contains("ana jay"))
    }

    @Test
    fun `two seals of the same text differ`() =
        assertNotEquals(Cipher.sealText(key, "x"), Cipher.sealText(key, "x"))

    @Test
    fun `wrong key gives null`() = assertNull(Cipher.open(otherKey, Cipher.seal(key, "x".toByteArray())))

    @Test
    fun `tampered data gives null`() {
        val sealed = Cipher.seal(key, "x".toByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertNull(Cipher.open(key, sealed))
    }

    @Test
    fun `text form is prefixed base64`() {
        val text = Cipher.sealText(key, "hello")
        assertTrue(text.startsWith("k1:"))
        assertEquals("hello", Cipher.openText(key, text))
        assertNull(Cipher.openText(key, "hello"))
    }
}

class SecureParserTest {
    private val key = SecretCode.key("Abcdefghijklmnopqrstuvwxyz012345")

    private fun line(id: String, message: String, attachmentUrl: String? = null): String {
        val json = JSONObject().put("id", id).put("time", 50).put("event", "message").put("message", message)
        if (attachmentUrl != null) {
            json.put("attachment", JSONObject().put("name", "s.bin").put("type", "application/octet-stream").put("url", attachmentUrl))
        }
        return json.toString()
    }

    @Test
    fun `encrypted text message`() = assertEquals(
        NtfyMessage("a", 50, "ana jay"),
        NtfyParser.parse(line("a", Cipher.sealText(key, Envelope.text("ana jay"))), key),
    )

    @Test
    fun `encrypted sticker with text keeps the attachment url`() = assertEquals(
        NtfyMessage("b", 50, "coucou", "https://ntfy.sh/file/b.bin"),
        NtfyParser.parse(line("b", Cipher.sealText(key, Envelope.text("coucou", sticker = true)), "https://ntfy.sh/file/b.bin"), key),
    )

    @Test
    fun `encrypted sticker alone`() = assertEquals(
        NtfyMessage("c", 50, null, "https://ntfy.sh/file/c.bin"),
        NtfyParser.parse(line("c", Cipher.sealText(key, Envelope.text(null, sticker = true)), "https://ntfy.sh/file/c.bin"), key),
    )

    @Test
    fun `encrypted poll`() = assertEquals(
        NtfyMessage("d", 50, "Pizza ?", null, Poll(listOf("🍕", "🍣"))),
        NtfyParser.parse(line("d", Cipher.sealText(key, Envelope.poll("Pizza ?", listOf("🍕", "🍣")))), key),
    )

    @Test
    fun `plaintext messages are refused`() = assertNull(NtfyParser.parse(line("e", "ana jay"), key))

    @Test
    fun `messages for another key are refused`() {
        val other = SecretCode.key("Zbcdefghijklmnopqrstuvwxyz012345")
        assertNull(NtfyParser.parse(line("f", Cipher.sealText(other, Envelope.text("x"))), key))
    }

    @Test
    fun `ping line is detected only with the right key`() {
        val ping = line("g", Cipher.sealText(key, Envelope.ping()))
        assertTrue(NtfyParser.isPing(ping, key))
        assertTrue(!NtfyParser.isPing(ping, SecretCode.key("Zbcdefghijklmnopqrstuvwxyz012345")))
        assertTrue(!NtfyParser.isPing(line("h", Cipher.sealText(key, Envelope.text("x"))), key))
    }

    @Test
    fun `phone ping is recognised`() =
        assertTrue(Envelope.isPing(Cipher.openText(key, Cipher.sealText(key, Envelope.ping()))!!))

    @Test
    fun `sender name travels inside the encryption`() = assertEquals(
        "Sara",
        NtfyParser.parse(line("i", Cipher.sealText(key, Envelope.text("salut", from = "Sara"))), key)!!.from,
    )

    @Test
    fun `poll keeps the sender name`() = assertEquals(
        "Sara",
        NtfyParser.parse(line("j", Cipher.sealText(key, Envelope.poll("?", listOf("a", "b"), from = "Sara"))), key)!!.from,
    )

    @Test
    fun `no or blank sender name gives null`() {
        assertNull(NtfyParser.parse(line("k", Cipher.sealText(key, Envelope.text("x"))), key)!!.from)
        assertNull(NtfyParser.parse(line("l", Cipher.sealText(key, Envelope.text("x", from = "   "))), key)!!.from)
    }

    @Test
    fun `sender name is trimmed and capped at 20`() = assertEquals(
        "a".repeat(20),
        NtfyParser.parse(line("m", Cipher.sealText(key, Envelope.text("x", from = "  " + "a".repeat(30)))), key)!!.from,
    )
}
