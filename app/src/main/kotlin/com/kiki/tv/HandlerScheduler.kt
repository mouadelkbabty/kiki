package com.kiki.tv

import android.os.Handler
import android.os.Looper
import com.kiki.core.Cancellable
import com.kiki.core.Scheduler

/** Runs delayed actions on the main thread. */
class HandlerScheduler(private val handler: Handler = Handler(Looper.getMainLooper())) : Scheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return Cancellable { handler.removeCallbacks(runnable) }
    }
}
