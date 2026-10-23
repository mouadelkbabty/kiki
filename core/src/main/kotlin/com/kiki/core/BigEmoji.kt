package com.kiki.core

object BigEmoji {
    private const val MAX = 3
    private const val ZWJ = 0x200D
    private const val VS16 = 0xFE0F

    /** True when the text is only 1 to 3 emojis (ZWJ sequences, skin tones and flags count as one). */
    fun isBigEmoji(text: String): Boolean {
        var clusters = 0
        var joinNext = false
        var pendingFlag = false
        for (cp in text.trim().codePoints()) {
            when {
                cp == VS16 -> Unit
                cp == ZWJ -> joinNext = true
                cp in 0x1F3FB..0x1F3FF -> Unit // skin tone modifies the previous emoji
                cp in 0x1F1E6..0x1F1FF -> { // regional indicators pair into a flag
                    if (pendingFlag) pendingFlag = false else { clusters++; pendingFlag = true }
                }
                isEmojiBase(cp) -> {
                    if (joinNext) joinNext = false else clusters++
                    pendingFlag = false
                }
                else -> return false
            }
        }
        return clusters in 1..MAX
    }

    private fun isEmojiBase(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF ||
            cp in 0x2600..0x27BF ||
            cp in 0x2300..0x23FF ||
            cp in 0x2B00..0x2BFF ||
            cp == 0x2764 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299
}
