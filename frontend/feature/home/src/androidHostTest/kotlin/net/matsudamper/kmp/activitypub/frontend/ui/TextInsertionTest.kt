package net.matsudamper.kmp.activitypub.frontend.ui

import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class TextInsertionTest {
    @Test
    fun `カーソルの位置に入れ、カーソルを入れた文字の後ろに置く`() {
        val inserted = TextInsertion(id = "1", text = "🎉", separatedFromWords = false).insertInto(current = "abcd", selectionStart = 2, selectionEnd = 2)

        assertEquals(InsertedText(text = "ab🎉cd", caret = 4), inserted)
    }

    @Test
    fun `選んでいる範囲は入れる文字で置き換える`() {
        val inserted = TextInsertion(id = "1", text = "X", separatedFromWords = false).insertInto(current = "abcd", selectionStart = 1, selectionEnd = 3)

        assertEquals(InsertedText(text = "aXd", caret = 2), inserted)
    }

    @Test
    fun `区切るものは、文字に続くときだけ前に空白を入れ、後ろには必ず入れる`() {
        val insertion = TextInsertion(id = "1", text = ":blobcat:", separatedFromWords = true)

        assertEquals(InsertedText(text = "hello :blobcat: ", caret = 16), insertion.insertInto(current = "hello", selectionStart = 5, selectionEnd = 5))
        assertEquals(InsertedText(text = "hello :blobcat: ", caret = 16), insertion.insertInto(current = "hello ", selectionStart = 6, selectionEnd = 6))
        assertEquals(InsertedText(text = ":blobcat: ", caret = 10), insertion.insertInto(current = "", selectionStart = 0, selectionEnd = 0))
    }

    @Test
    fun `本文の外を指す位置は本文の中に丸める`() {
        val inserted = TextInsertion(id = "1", text = "X", separatedFromWords = false).insertInto(current = "ab", selectionStart = 9, selectionEnd = 1)

        assertEquals(InsertedText(text = "abX", caret = 3), inserted)
    }
}
