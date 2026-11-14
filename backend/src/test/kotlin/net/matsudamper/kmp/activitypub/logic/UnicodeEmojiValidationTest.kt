package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnicodeEmojiValidationTest {
    @Test
    fun acceptsSingleEmojiMadeOfSeveralCodePoints() {
        listOf("👍", "ℹ️", "ℹ", "1️⃣", "#️⃣", "👍🏽", "👨‍👩‍👧‍👦", "🇯🇵", "©️").forEach { emoji ->
            assertTrue(isUnicodeEmoji(emoji), emoji)
        }
    }

    @Test
    fun rejectsTextAndMoreThanOneEmoji() {
        listOf("", "👍👍", "日本語", "a", ":kotlin:", "👍 ", "1", "#", "*", "1\u0301").forEach { text ->
            assertFalse(isUnicodeEmoji(text), text)
        }
    }
}
