package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnicodeEmojiValidationTest {
    @Test
    fun acceptsSingleEmojiMadeOfSeveralCodePoints() {
        listOf("👍", "ℹ️", "1️⃣", "👍🏽", "👨‍👩‍👧‍👦", "🇯🇵").forEach { emoji ->
            assertTrue(isUnicodeEmoji(emoji), emoji)
        }
    }

    @Test
    fun rejectsTextAndMoreThanOneEmoji() {
        listOf("", "👍👍", "日本語", "a", ":kotlin:", "👍 ").forEach { text ->
            assertFalse(isUnicodeEmoji(text), text)
        }
    }
}
