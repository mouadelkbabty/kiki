package com.kiki.core

import org.json.JSONObject

/** Messages the TV sends back to the phone page, on a second ntfy topic. */
object Replies {
    fun topicFor(topic: String): String = "$topic-r"

    fun seen(id: String, timeSec: Long): String =
        JSONObject().put("type", "seen").put("id", id).put("time", timeSec).toString()

    fun vote(id: String, choice: String, timeSec: Long): String =
        JSONObject().put("type", "vote").put("id", id).put("choice", choice).put("time", timeSec).toString()

    fun status(on: Boolean, timeSec: Long): String =
        JSONObject().put("type", "status").put("state", if (on) "on" else "off").put("time", timeSec).toString()
}
