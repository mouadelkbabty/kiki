package com.kiki.core

/**
 * A message received from ntfy. [time] is in epoch seconds, [text] is already normalized.
 * A message has a [text], a sticker [imageUrl], or both.
 */
data class NtfyMessage(val id: String, val time: Long, val text: String?, val imageUrl: String? = null)
