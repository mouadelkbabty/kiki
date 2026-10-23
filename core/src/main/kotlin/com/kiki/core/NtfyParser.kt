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
            val poll = parsePoll(raw)
            val text = when {
                poll != null -> poll.second
                attachment != null && raw.startsWith(AUTO_FILE_TEXT) -> null
                else -> MessageText.normalize(raw)
            }
            if (text == null && imageUrl == null) {
                null
            } else {
                NtfyMessage(json.getString("id"), json.getLong("time"), text, imageUrl, poll?.first)
            }
        }
    } catch (e: Exception) {
        null
    }

    /** A poll arrives as `{"kiki":"poll","q":…,"choices":[…]}` in the message body. Returns poll + question. */
    private fun parsePoll(raw: String): Pair<Poll, String>? = try {
        val body = JSONObject(raw)
        val array = body.getJSONArray("choices")
        val choices = (0 until array.length()).mapNotNull { MessageText.normalize(array.getString(it)) }
        val question = MessageText.normalize(body.optString("q"))
        if (body.optString("kiki") != "poll" || question == null || choices.size !in 2..3) {
            null
        } else {
            Poll(choices) to question
        }
    } catch (e: Exception) {
        null
    }
}
