package com.kiki.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Invisible activity used once over ADB: `am start -n com.kiki.tv/.ConfigActivity --es topic <secret>`. */
class ConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val topic = intent.getStringExtra("topic")
        if (topic == null || !TOPIC.matches(topic)) {
            Toast.makeText(this, "Code invalide", Toast.LENGTH_LONG).show()
        } else {
            Prefs(this).topic = topic
            val service = Intent(this, ListenerService::class.java)
            stopService(service)
            startForegroundService(service)
        }
        finish()
    }

    private companion object {
        val TOPIC = Regex("^[A-Za-z0-9]{40}$")
    }
}
