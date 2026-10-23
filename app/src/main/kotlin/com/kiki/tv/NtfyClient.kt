package com.kiki.tv

import com.kiki.core.NtfyMessage
import com.kiki.core.NtfyParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Talks to ntfy.sh over plain HTTP streaming. */
class NtfyClient(private val topic: String) {
    @Volatile
    private var current: HttpURLConnection? = null

    /** Cached messages since [since] ("12h", a message id…). */
    fun poll(since: String): List<NtfyMessage> {
        val conn = open("https://ntfy.sh/$topic/json?poll=1&since=$since")
        try {
            return conn.inputStream.bufferedReader().readLines().mapNotNull { NtfyParser.parse(it) }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Blocks while the stream is open. [onOpen] runs once connected; each message goes to [onMessage].
     * Throws IOException when the connection drops.
     */
    fun stream(since: String, onOpen: () -> Unit, onMessage: (NtfyMessage) -> Unit) {
        val conn = open("https://ntfy.sh/$topic/json?since=$since")
        current = conn
        try {
            val reader = conn.inputStream.bufferedReader()
            onOpen()
            while (true) {
                val line = reader.readLine() ?: throw IOException("stream closed")
                NtfyParser.parse(line)?.let(onMessage)
            }
        } finally {
            current = null
            conn.disconnect()
        }
    }

    /** Unblocks a running [stream] (used when the service stops). */
    fun close() {
        current?.disconnect()
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        // ntfy sends a keepalive every 45 s; a silent minute and a half means the link is dead.
        conn.readTimeout = 90_000
        if (conn.responseCode != 200) {
            conn.disconnect()
            throw IOException("ntfy HTTP ${conn.responseCode}")
        }
        return conn
    }
}
