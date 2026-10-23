package com.kiki.core

import org.json.JSONObject

object NtfyParser {
    /** ntfy fills the message with this when a file is sent without text. */
    private const val AUTO_FILE_TEXT = "You received a file:"

    /** Parses one line of an ntfy JSON stream. Anything other than a usable message gives null. */
    fun parse(line: String): NtfyMessage? = try {
        val json = JSONObject(line)
        if (json.optString("event") != "message") {
            null
        } else {
            val attachment = json.optJSONObject("attachment")
            val imageUrl = attachment
                ?.takeIf { it.optString("type").startsWith("image/") }
                ?.optString("url")
                ?.takeIf { it.isNotEmpty() }
            val raw = json.optString("message")
            val text = if (attachment != null && raw.startsWith(AUTO_FILE_TEXT)) null else MessageText.normalize(raw)
            if (text == null && imageUrl == null) {
                null
            } else {
                NtfyMessage(json.getString("id"), json.getLong("time"), text, imageUrl)
            }
        }
    } catch (e: Exception) {
        null
    }
}
