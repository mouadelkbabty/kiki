package com.kiki.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.kiki.core.Backoff
import com.kiki.core.BigEmoji
import com.kiki.core.BubbleController
import com.kiki.core.Replies
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

    private val overlay by lazy { BubbleOverlay(this) }
    private val emojiOverlay by lazy { EmojiOverlay(this) }
    private var replies: NtfyPublisher? = null

    private val bubble by lazy {
        BubbleController(overlay, HandlerScheduler(mainHandler)).also { controller ->
            overlay.onDismiss = {
                controller.onUserKey()?.let { replies?.publish(Replies.seen(it.id, nowSec())) }
            }
            overlay.onVote = { choice ->
                controller.onUserKey()?.let { replies?.publish(Replies.vote(it.id, choice, nowSec())) }
            }
        }
    }

    /** Tells the phone page when the screen turns on or off. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> sendStatus(on = true)
                Intent.ACTION_SCREEN_OFF -> sendStatus(on = false)
            }
        }
    }

    /** While the screen is on, a small "still here" every 15 min so the page can spot a dead TV. */
    private val heartbeat = object : Runnable {
        override fun run() {
            replies?.publish(Replies.status(on = true, timeSec = nowSec()))
            mainHandler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private fun sendStatus(on: Boolean) {
        mainHandler.removeCallbacks(heartbeat)
        if (on) {
            heartbeat.run()
        } else {
            replies?.publish(Replies.status(on = false, timeSec = nowSec()))
        }
    }

    private fun nowSec() = System.currentTimeMillis() / 1000

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        if (intent?.action == ACTION_TEST) {
            bubble.onMessage(NtfyMessage("test", System.currentTimeMillis() / 1000, "Ceci est un test 👋"))
        }
        val topic = Prefs(this).topic
        if (topic == null && intent?.action == ACTION_TEST) return START_NOT_STICKY
        if (topic == null) {
            Log.w(TAG, "no topic configured")
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker == null) {
            running = true
            client = NtfyClient(topic)
            replies = NtfyPublisher(Replies.topicFor(topic))
            registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
            )
            sendStatus(on = true)
            worker = Thread({ loop(client!!) }, "kiki-listener").also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (replies != null) unregisterReceiver(screenReceiver)
        mainHandler.removeCallbacks(heartbeat)
        replies?.shutdown()
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
                val nowSec = System.currentTimeMillis() / 1000
                StartupPicker.pick(cached, nowSec, prefs.lastShownId)?.let { show(it) }
                val since = StartupPicker.streamSince(cached, nowSec)
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

    /** Worker thread: fetches the sticker first so the bubble appears complete. */
    private fun show(message: NtfyMessage) {
        Prefs(this).lastShownId = message.id
        val text = message.text
        if (text != null && message.imageUrl == null && message.poll == null && BigEmoji.isBigEmoji(text)) {
            mainHandler.post {
                emojiOverlay.show(text)
            }
            return
        }
        val sticker = message.imageUrl?.let { StickerLoader.load(it, STICKER_TARGET_PX) }
        mainHandler.post {
            overlay.nextSticker = sticker
            bubble.onMessage(message)
        }
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
        const val ACTION_TEST = "com.kiki.tv.TEST"
        private const val STICKER_TARGET_PX = 200
        private const val HEARTBEAT_MS = 15 * 60 * 1000L
        private const val TAG = "kiki"
        private const val CHANNEL_ID = "listener"
        private const val NOTIFICATION_ID = 1
    }
}
