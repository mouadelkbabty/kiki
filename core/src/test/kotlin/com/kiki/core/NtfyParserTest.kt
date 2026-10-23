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

    @Test
    fun `sticker with text keeps both`() = assertEquals(
        NtfyMessage("s1", 5, "ana jay", "https://ntfy.sh/file/abc.png"),
        NtfyParser.parse(
            """{"id":"s1","time":5,"event":"message","topic":"t","message":"ana jay",""" +
                """"attachment":{"name":"sticker.png","type":"image/png","url":"https://ntfy.sh/file/abc.png"}}""",
        ),
    )

    @Test
    fun `sticker alone has no text`() = assertEquals(
        NtfyMessage("s2", 6, null, "https://ntfy.sh/file/def.png"),
        NtfyParser.parse(
            """{"id":"s2","time":6,"event":"message","topic":"t","message":"You received a file: sticker.png",""" +
                """"attachment":{"name":"sticker.png","type":"image/png","url":"https://ntfy.sh/file/def.png"}}""",
        ),
    )

    @Test
    fun `non image attachment is ignored`() = assertNull(
        NtfyParser.parse(
            """{"id":"s3","time":7,"event":"message","topic":"t","message":"You received a file: a.pdf",""" +
                """"attachment":{"name":"a.pdf","type":"application/pdf","url":"https://ntfy.sh/file/a.pdf"}}""",
        ),
    )
}
