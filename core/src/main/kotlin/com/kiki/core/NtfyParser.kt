package com.kiki.core

import org.json.JSONObject

object NtfyParser {
    /**
     * Parses one line of an ntfy JSON stream. Only messages sealed with [key] are accepted;
     * plaintext, other keys, pings and broken lines give null.
     */
    fun parse(line: String, key: ByteArray): NtfyMessage? = try {
        parseOrNull(line, key)
    } catch (e: Exception) {
        null
    }

    private fun parseOrNull(line: String, key: ByteArray): NtfyMessage? {
        val json = JSONObject(line)
        if (json.optString("event") != "message") return null
        val plain = Cipher.openText(key, json.optString("message")) ?: return null
        val body = JSONObject(plain)
        val id = json.getString("id")
        val time = json.getLong("time")
        return when (body.optString("type")) {
            "msg" -> {
                val text = if (body.isNull("text")) null else MessageText.normalize(body.optString("text"))
                val imageUrl = if (body.optBoolean("sticker")) {
                    json.optJSONObject("attachment")?.optString("url")?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                if (text == null && imageUrl == null) null else NtfyMessage(id, time, text, imageUrl)
            }
            "poll" -> {
                val array = body.getJSONArray("choices")
                val choices = (0 until array.length()).mapNotNull { MessageText.normalize(array.getString(it)) }
                val question = MessageText.normalize(body.optString("q"))
                if (question == null || choices.size !in 2..3) null else NtfyMessage(id, time, question, null, Poll(choices))
            }
            else -> null
        }
    }
}
