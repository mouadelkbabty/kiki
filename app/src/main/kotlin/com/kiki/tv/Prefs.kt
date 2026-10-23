package com.kiki.tv

import android.content.Context
import com.kiki.core.SecretCode

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("kiki", Context.MODE_PRIVATE)

    /** The shared secret code; topic and encryption key are derived from it. */
    var code: String?
        get() = prefs.getString("code", null)
        set(value) = prefs.edit().clear().putString("code", value).apply()

    val topic: String? get() = code?.let(SecretCode::topic)
    val key: ByteArray? get() = code?.let(SecretCode::key)

    var lastShownId: String?
        get() = prefs.getString("lastShownId", null)
        set(value) = prefs.edit().putString("lastShownId", value).apply()
}
