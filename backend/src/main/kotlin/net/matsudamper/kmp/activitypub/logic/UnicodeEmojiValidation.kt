package net.matsudamper.kmp.activitypub.logic

/**
 * 絵文字 1 つだけかを見る。UTS #51 の絵文字の並びの形（キーキャップ、国旗、タグ、ZWJ で繋いだ並び）のどれかに合うものだけを通す。
 * 肌の色や家族の絵文字は、いくつものコードポイントを繋いで 1 つになる。
 * ℹ️ のように文字にも分類される絵文字があるので、文字の分類ではなく絵文字の性質で判定する
 */
internal fun isUnicodeEmoji(text: String): Boolean {
    val codePoints = text.codePoints().toArray()
    if (codePoints.isEmpty() || codePoints.size > MAX_CODE_POINTS) return false
    return isKeycap(codePoints) || isFlag(codePoints) || isTagSequence(codePoints) || isZwjSequence(codePoints)
}

/**
 * 1️⃣ や #️⃣。数字や # は、それだけでは絵文字として出ない
 */
private fun isKeycap(codePoints: IntArray): Boolean {
    val base = codePoints.first()
    val body = codePoints.drop(1)
    return base in KEYCAP_BASES && (body == listOf(COMBINING_ENCLOSING_KEYCAP) || body == listOf(VARIATION_SELECTOR_16, COMBINING_ENCLOSING_KEYCAP))
}

/**
 * 🇯🇵 のように地域指示記号を 2 つ並べたもの
 */
private fun isFlag(codePoints: IntArray): Boolean = codePoints.size == 2 && codePoints.all { it in REGIONAL_INDICATORS }

/**
 * 🏴 にタグ文字を続けて終わりの印で閉じたもの。イングランドの旗など
 */
private fun isTagSequence(codePoints: IntArray): Boolean =
    codePoints.size >= 3 &&
        codePoints.first() == BLACK_FLAG &&
        codePoints.last() == CANCEL_TAG &&
        codePoints.drop(1).dropLast(1).all { it in TAG_CHARACTERS }

/**
 * ZWJ で繋いだ並び。ZWJ が無ければ絵文字 1 つ。
 * 繋ぐ 1 つずつは、絵文字の文字に、絵文字で出す印か肌の色をそれぞれ 1 つまで付けたもの
 */
private fun isZwjSequence(codePoints: IntArray): Boolean {
    val elements = codePoints.fold(listOf(listOf<Int>())) { elements, codePoint ->
        if (codePoint == ZERO_WIDTH_JOINER) elements + listOf(listOf()) else elements.dropLast(1) + listOf(elements.last() + codePoint)
    }
    return elements.all { isEmojiElement(it) }
}

private fun isEmojiElement(element: List<Int>): Boolean {
    val base = element.firstOrNull() ?: return false
    // 地域指示記号は 2 つ並べた国旗でしか絵文字にならない
    if (base in REGIONAL_INDICATORS) return false
    // Extended_Pictographic には ⎈ のような絵文字ではない記号が、Emoji には数字や # が含まれるので、両方を見る
    if (!Character.isEmoji(base)) return false
    if (!Character.isExtendedPictographic(base) && !Character.isEmojiPresentation(base)) return false
    val modifiers = element.drop(1)
    val allowed = modifiers.all { it == VARIATION_SELECTOR_16 || Character.isEmojiModifier(it) }
    val skinToneCount = modifiers.count { Character.isEmojiModifier(it) }
    val skinToneAllowed = skinToneCount == 0 || (skinToneCount == 1 && Character.isEmojiModifierBase(base))
    return allowed && modifiers.count { it == VARIATION_SELECTOR_16 } <= 1 && skinToneAllowed
}

// 家族の絵文字でも 11 コードポイントなので、余裕を持たせる
private const val MAX_CODE_POINTS = 16
private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR_16 = 0xFE0F
private const val COMBINING_ENCLOSING_KEYCAP = 0x20E3
private const val BLACK_FLAG = 0x1F3F4
private const val CANCEL_TAG = 0xE007F
private val KEYCAP_BASES = "0123456789#*".map { it.code }.toSet()
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
private val TAG_CHARACTERS = 0xE0020..0xE007E
