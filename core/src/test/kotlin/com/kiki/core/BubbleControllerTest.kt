package com.kiki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleControllerTest {
    private class FakeTask(val delayMs: Long, val action: () -> Unit) : Cancellable {
        var cancelled = false
        override fun cancel() { cancelled = true }
        fun run() = action()
    }

    private class FakeScheduler : Scheduler {
        val tasks = mutableListOf<FakeTask>()
        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable =
            FakeTask(delayMs, action).also { tasks += it }
    }

    private class FakeDisplay : BubbleDisplay {
        val shown = mutableListOf<NtfyMessage>()
        var hides = 0
        override fun show(message: NtfyMessage) { shown += message }
        override fun hide() { hides++ }
    }

    private val m1 = NtfyMessage("1", 100, "premier")
    private val m2 = NtfyMessage("2", 101, "second")
    private val scheduler = FakeScheduler()
    private val display = FakeDisplay()
    private val controller = BubbleController(display, scheduler)

    @Test
    fun `message shows and schedules 5 min hide`() {
        controller.onMessage(m1)
        assertEquals(listOf(m1), display.shown)
        assertEquals(1, scheduler.tasks.size)
        assertEquals(300_000L, scheduler.tasks[0].delayMs)
        assertTrue(controller.isShowing)
    }

    @Test
    fun `user key hides and cancels timer`() {
        controller.onMessage(m1)
        controller.onUserKey()
        assertEquals(1, display.hides)
        assertTrue(scheduler.tasks[0].cancelled)
        assertFalse(controller.isShowing)
    }

    @Test
    fun `key when nothing shown does nothing`() {
        controller.onUserKey()
        assertEquals(0, display.hides)
    }

    @Test
    fun `replace cancels previous timer`() {
        controller.onMessage(m1)
        controller.onMessage(m2)
        assertTrue(scheduler.tasks[0].cancelled)
        scheduler.tasks[0].run()
        assertEquals(0, display.hides)
        assertEquals(listOf(m1, m2), display.shown)
        assertTrue(controller.isShowing)
    }

    @Test
    fun `timeout hides`() {
        controller.onMessage(m1)
        scheduler.tasks[0].run()
        assertEquals(1, display.hides)
        assertFalse(controller.isShowing)
    }
}
