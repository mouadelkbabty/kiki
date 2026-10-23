package com.kiki.tv

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("kiki", Context.MODE_PRIVATE)

    var topic: String?
        get() = prefs.getString("topic", null)
        set(value) = prefs.edit().putString("topic", value).apply()

    var lastShownId: String?
        get() = prefs.getString("lastShownId", null)
        set(value) = prefs.edit().putString("lastShownId", value).apply()
}
