package com.kiki.tv

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Posts small JSON replies to the phone's reply topic, one at a time, off the main thread. */
class NtfyPublisher(private val topic: String) {
    private val executor = Executors.newSingleThreadExecutor()

    fun publish(body: String) {
        executor.execute {
            try {
                val conn = URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("kiki", "reply not sent: ${e.message}")
            }
        }
    }

    fun shutdown() = executor.shutdown()
}
