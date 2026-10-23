package com.kiki.core

/** A message received from ntfy. [time] is in epoch seconds, [text] is already normalized. */
data class NtfyMessage(val id: String, val time: Long, val text: String)
