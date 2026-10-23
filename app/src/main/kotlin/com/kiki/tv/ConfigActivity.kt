package com.kiki.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.kiki.core.SecretCode

/** Invisible activity used once over ADB: `am start -n com.kiki.tv/.ConfigActivity --es code <secret>`. */
class ConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.getStringExtra("code")
        if (code == null || !SecretCode.isValid(code)) {
            Toast.makeText(this, "Code invalide", Toast.LENGTH_LONG).show()
        } else {
            // Replaces everything stored before (old topic, last shown message).
            Prefs(this).code = code
            val service = Intent(this, ListenerService::class.java)
            stopService(service)
            startForegroundService(service)
        }
        finish()
    }
}
