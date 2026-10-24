package com.kiki.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible, closes at once: starts the listener from the foreground.
 *
 * A service started straight from the boot broadcast never becomes a foreground service on this
 * TCL TV, and TCL's memory manager then freezes the process. Started from an activity (allowed
 * from the background because kiki may draw over other apps), it stays foreground and alive.
 */
class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, ListenerService::class.java))
        finish()
        overridePendingTransition(0, 0)
    }
}
