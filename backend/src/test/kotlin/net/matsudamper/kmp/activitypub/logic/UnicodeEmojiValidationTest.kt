package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnicodeEmojiValidationTest {
    @Test
    fun acceptsSingleEmojiMadeOfSeveralCodePoints() {
        listOf("👍", "ℹ️", "ℹ", "1️⃣", "#️⃣", "👍🏽", "👨‍👩‍👧‍👦", "🇯🇵", "©️", "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F", "🧑🏽‍💻").forEach { emoji ->
            assertTrue(isUnicodeEmoji(emoji), emoji)
        }
    }

    @Test
    fun rejectsTextAndMoreThanOneEmoji() {
        listOf("", "👍👍", "日本語", "a", ":kotlin:", "👍 ", "1", "#", "*", "1\u0301", "1\u0301\u20E3", "日\u20E3", "👍\u0301", "🇯", "🇯🇵🇺🇸").forEach { text ->
            assertFalse(isUnicodeEmoji(text), text)
        }
    }
}
