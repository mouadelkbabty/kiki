package com.kiki.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.kiki.core.Cipher
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads an encrypted sticker, decrypts it and decodes it at roughly [targetPx]. Worker thread only. */
object StickerLoader {
    private const val MAX_BYTES = 2_000_000

    fun load(url: String, key: ByteArray, targetPx: Int): Bitmap? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        val sealed = try {
            conn.inputStream.use { it.readCapped(MAX_BYTES) }
        } finally {
            conn.disconnect()
        }
        val bytes = Cipher.open(key, sealed) ?: throw IllegalStateException("sticker not sealed with our key")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        Log.w("kiki", "sticker not loaded: ${e.message}")
        null
    }

    private fun InputStream.readCapped(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
