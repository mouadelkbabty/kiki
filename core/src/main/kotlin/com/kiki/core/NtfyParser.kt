package com.kiki.core

import org.json.JSONObject

object NtfyParser {
    /** Parses one line of an ntfy JSON stream. Anything other than a usable message gives null. */
    fun parse(line: String): NtfyMessage? = try {
        val json = JSONObject(line)
        if (json.optString("event") != "message") {
            null
        } else {
            MessageText.normalize(json.optString("message"))?.let { text ->
                NtfyMessage(json.getString("id"), json.getLong("time"), text)
            }
        }
    } catch (e: Exception) {
        null
    }
}
