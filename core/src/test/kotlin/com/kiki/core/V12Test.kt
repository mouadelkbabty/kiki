package com.kiki.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BigEmojiTest {
    @Test
    fun `single heart is big`() = assertTrue(BigEmoji.isBigEmoji("❤️"))

    @Test
    fun `three emojis are big`() = assertTrue(BigEmoji.isBigEmoji("😘😘😘"))

    @Test
    fun `family emoji with zero width joiners counts as one`() = assertTrue(BigEmoji.isBigEmoji("👨‍👩‍👧"))

    @Test
    fun `four emojis are not big`() = assertFalse(BigEmoji.isBigEmoji("😘😘😘😘"))

    @Test
    fun `text with emoji is not big`() = assertFalse(BigEmoji.isBigEmoji("ana jay 🥰"))

    @Test
    fun `digits are not emoji`() = assertFalse(BigEmoji.isBigEmoji("10"))
}

class PollSelectionTest {
    @Test
    fun `starts on first choice and stays within bounds`() {
        val s = PollSelection(3)
        assertEquals(0, s.index)
        s.left()
        assertEquals(0, s.index)
        s.right(); s.right(); s.right()
        assertEquals(2, s.index)
    }
}

class RepliesTest {
    @Test
    fun `seen reply`() {
        val json = JSONObject(Replies.seen("abc", 100))
        assertEquals("seen", json.getString("type"))
        assertEquals("abc", json.getString("id"))
        assertEquals(100L, json.getLong("time"))
    }

    @Test
    fun `vote reply`() {
        val json = JSONObject(Replies.vote("p1", "🍕 Pizza", 101))
        assertEquals("vote", json.getString("type"))
        assertEquals("p1", json.getString("id"))
        assertEquals("🍕 Pizza", json.getString("choice"))
    }

    @Test
    fun `status reply`() {
        val json = JSONObject(Replies.status(on = false, timeSec = 102))
        assertEquals("status", json.getString("type"))
        assertEquals("off", json.getString("state"))
    }

    @Test
    fun `reply topic adds suffix`() = assertEquals("abc-r", Replies.topicFor("abc"))
}

class BubbleDismissTest {
    @Test
    fun `user key returns the dismissed message`() {
        val display = object : BubbleDisplay {
            override fun show(message: NtfyMessage) {}
            override fun hide() {}
        }
        val scheduler = object : Scheduler {
            override fun schedule(delayMs: Long, action: () -> Unit) = Cancellable {}
        }
        val controller = BubbleController(display, scheduler)
        val m = NtfyMessage("x", 1, "hi")
        controller.onMessage(m)
        assertEquals(m, controller.onUserKey())
        assertNull(controller.onUserKey())
    }
}
