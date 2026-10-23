package com.kiki.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Status screen opened from the home screen. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView

    private val refresh = object : Runnable {
        override fun run() {
            status.text = when {
                Prefs(this@MainActivity).topic == null -> "Pas configuré"
                ListenerStatus.connected -> "Connecté"
                else -> "Reconnexion…"
            }
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            gravity = Gravity.CENTER
        }
        val test = Button(this).apply {
            text = "Afficher un message test"
            setOnClickListener {
                startForegroundService(
                    Intent(this@MainActivity, ListenerService::class.java).setAction(ListenerService.ACTION_TEST),
                )
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#0B0B0F"))
                addView(status)
                addView(test)
            },
        )
        test.requestFocus()
        if (Prefs(this).topic != null) startForegroundService(Intent(this, ListenerService::class.java))
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
