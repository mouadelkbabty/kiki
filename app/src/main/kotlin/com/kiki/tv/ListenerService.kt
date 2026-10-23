package com.kiki.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.kiki.core.Backoff
import com.kiki.core.BubbleController
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

    private val bubble by lazy {
        BubbleController(overlay, HandlerScheduler(mainHandler)).also { controller ->
            overlay.onKey = { controller.onUserKey() }
        }
    }

    /**
     * Main thread. A fresh MediaPlayer per ding: this TV's SoundPool fast path stays silent.
     * Media usage follows the TV's own volume (its notification volume sits near zero).
     */
    private fun ding() {
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            resources.openRawResourceFd(R.raw.ding).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.setVolume(DING_VOLUME, DING_VOLUME)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "ding error $what/$extra")
                mp.release()
                true
            }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "ding failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        if (intent?.action == ACTION_TEST) {
            bubble.onMessage(NtfyMessage("test", System.currentTimeMillis() / 1000, "Ceci est un test 👋"))
            ding()
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
        val sticker = message.imageUrl?.let { StickerLoader.load(it, STICKER_TARGET_PX) }
        mainHandler.post {
            overlay.nextSticker = sticker
            bubble.onMessage(message)
            ding()
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
        private const val DING_VOLUME = 1.0f
        private const val TAG = "kiki"
        private const val CHANNEL_ID = "listener"
        private const val NOTIFICATION_ID = 1
    }
}
