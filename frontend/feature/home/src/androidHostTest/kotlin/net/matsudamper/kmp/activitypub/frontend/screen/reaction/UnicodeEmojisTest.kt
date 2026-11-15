package net.matsudamper.kmp.activitypub.frontend.screen.reaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnicodeEmojisTest {
    @Test
    fun searchFindsByJapaneseNameRegardlessOfKatakanaOrHiragana() {
        val byHiragana = UnicodeEmojis.search("ねこ").map { it.emoji }
        val byKatakana = UnicodeEmojis.search("ネコ").map { it.emoji }

        assertTrue("🐈" in byHiragana)
        assertEquals(byHiragana, byKatakana)
    }

    @Test
    fun searchFindsByEnglishNameRegardlessOfCase() {
        assertTrue("👍" in UnicodeEmojis.search("THUMBS UP").map { it.emoji })
    }

    @Test
    fun blankQueryReturnsEverythingInCldrOrder() {
        val all = UnicodeEmojis.search("  ")

        assertEquals(UnicodeEmojis.all, all)
        assertEquals("😀", all.first().emoji)
        assertEquals(UnicodeEmojiCategory.SmileysAndEmotion, all.first().category)
    }

    @Test
    fun skinToneVariantsAreNotListed() {
        val skinTones = (0x1F3FB..0x1F3FF).map { codePoint -> String(Character.toChars(codePoint)) }

        assertTrue(UnicodeEmojis.all.none { emoji -> skinTones.any { emoji.emoji.contains(it) } })
    }
}
