package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlToPlainTextTest {
    @Test
    fun 投稿の段落と改行を復元する() {
        val text = "first\nline\n\nsecond"
        val composed = assertIs<NoteComposer.ComposeResult.Composed>(NoteComposer.compose(text))

        assertEquals(text, htmlToPlainText(composed.contentHtml))
    }

    @Test
    fun 自己紹介の段落と改行とリンクのテキストを保持する() {
        val html = "<p>first<br>line<br />next</p><p><a href=\"https://example.com\">second</a> &amp; &lt;third&gt;</p>"

        assertEquals("first\nline\nnext\n\nsecond & <third>", htmlToPlainText(html))
    }

    @Test
    fun 段落の前後にあるテキストも区切る() {
        assertEquals("first\n\nsecond\n\nthird", htmlToPlainText("first<p>second</p>third"))
    }

    @Test
    fun プレーンテキストの改行を保持する() {
        assertEquals("first\n\nsecond", htmlToPlainText("first\n\nsecond"))
        assertEquals("", htmlToPlainText(""))
    }
}
