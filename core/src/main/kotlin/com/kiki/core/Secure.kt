package com.kiki.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The one secret shared by the phone and the TV. Both the ntfy topic and the encryption key are
 * derived from it, so ntfy only ever learns the topic, never the key.
 */
object SecretCode {
    private val FORMAT = Regex("^[A-Za-z0-9]{32}$")

    fun isValid(code: String): Boolean = FORMAT.matches(code)

    /** First 40 hex chars of sha256("kiki-topic:" + code). */
    fun topic(code: String): String = sha256("kiki-topic:$code").joinToString("") { "%02x".format(it) }.take(40)

    /** sha256("kiki-key:" + code): a 256-bit AES key. */
    fun key(code: String): ByteArray = sha256("kiki-key:$code")

    private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
}

/** AES-256-GCM. Sealed form: 12-byte random IV followed by ciphertext and 16-byte tag (same as WebCrypto). */
object Cipher {
    private const val PREFIX = "k1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    /** Null when the key is wrong or the data was altered. */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray? = try {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
        )
        cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    } catch (e: Exception) {
        null
    }

    fun sealText(key: ByteArray, plain: String): String =
        PREFIX + Base64.getEncoder().encodeToString(seal(key, plain.toByteArray()))

    fun openText(key: ByteArray, sealed: String): String? {
        if (!sealed.startsWith(PREFIX)) return null
        val bytes = try {
            Base64.getDecoder().decode(sealed.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            return null
        }
        return open(key, bytes)?.toString(Charsets.UTF_8)
    }
}

/** What travels inside the encryption, from the phone to the TV. */
object Envelope {
    fun text(text: String?, sticker: Boolean = false): String =
        JSONObject().put("type", "msg").put("text", text ?: JSONObject.NULL).put("sticker", sticker).toString()

    fun poll(question: String, choices: List<String>): String =
        JSONObject().put("type", "poll").put("q", question).put("choices", JSONArray(choices)).toString()

    fun ping(): String = JSONObject().put("type", "ping").toString()

    fun isPing(plain: String): Boolean = try {
        JSONObject(plain).optString("type") == "ping"
    } catch (e: Exception) {
        false
    }
}
