package com.kiki.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.kiki.core.Backoff
import com.kiki.core.NtfyMessage
import com.kiki.core.StartupPicker

/** Keeps one ntfy stream open and hands every new message to the bubble. */
class ListenerService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null
    private var client: NtfyClient? = null

    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val topic = Prefs(this).topic
        if (topic == null) {
            Log.w(TAG, "no topic configured")
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker == null) {
            running = true
            client = NtfyClient(topic)
            worker = Thread({ loop(client!!) }, "kiki-listener").also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        client?.close()
        worker?.interrupt()
        ListenerStatus.connected = false
        super.onDestroy()
    }

    private fun loop(client: NtfyClient) {
        val prefs = Prefs(this)
        val backoff = Backoff()
        while (running) {
            try {
                val cached = client.poll("12h")
                StartupPicker.pick(cached, System.currentTimeMillis() / 1000, prefs.lastShownId)?.let { show(it) }
                val since = cached.maxByOrNull { it.time }?.id ?: "now"
                client.stream(
                    since,
                    onOpen = {
                        backoff.reset()
                        ListenerStatus.connected = true
                    },
                    onMessage = { show(it) },
                )
            } catch (e: Exception) {
                ListenerStatus.connected = false
                if (!running) break
                val delay = backoff.next()
                Log.i(TAG, "connection lost (${e.message}), retry in $delay ms")
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun show(message: NtfyMessage) {
        Prefs(this).lastShownId = message.id
        mainHandler.post { showMessage(message) }
    }

    /** Runs on the main thread. */
    private fun showMessage(message: NtfyMessage) {
        Log.i(TAG, "message ${message.id}: ${message.text}")
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.channel_name))
            .build()
    }

    companion object {
        private const val TAG = "kiki"
        private const val CHANNEL_ID = "listener"
        private const val NOTIFICATION_ID = 1
    }
}
