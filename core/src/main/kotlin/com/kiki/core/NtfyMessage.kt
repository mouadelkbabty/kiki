package com.kiki.core

/** A poll sent from the phone: 2 or 3 choices, answered with the remote. */
data class Poll(val choices: List<String>)

/**
 * A message received from ntfy. [time] is in epoch seconds, [text] is already normalized.
 * A message has a [text], a sticker [imageUrl], or both; a [poll] uses [text] as its question.
 */
data class NtfyMessage(
    val id: String,
    val time: Long,
    val text: String?,
    val imageUrl: String? = null,
    val poll: Poll? = null,
)
