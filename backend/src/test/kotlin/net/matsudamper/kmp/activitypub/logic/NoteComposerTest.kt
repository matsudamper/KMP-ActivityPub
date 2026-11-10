package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NoteComposerTest {
    @Test
    fun 本文のURLをリンクにして句読点と閉じ括弧は外に残す() {
        val composed = assertIs<NoteComposer.ComposeResult.Composed>(
            NoteComposer.compose("記事 https://example.com/a?x=1&y=2.\n（https://en.wikipedia.org/wiki/Kotlin_(programming_language)）"),
        )

        assertEquals(
            "<p>記事 <a href=\"https://example.com/a?x=1&amp;y=2\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">https://example.com/a?x=1&amp;y=2</a>.<br>" +
                "（<a href=\"https://en.wikipedia.org/wiki/Kotlin_(programming_language)\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">https://en.wikipedia.org/wiki/Kotlin_(programming_language)</a>）</p>",
            composed.contentHtml,
        )
    }

    @Test
    fun URLの外のタグはエスケープする() {
        val composed = assertIs<NoteComposer.ComposeResult.Composed>(
            NoteComposer.compose("<b>https://example.com</b>"),
        )

        assertEquals(
            "<p>&lt;b&gt;<a href=\"https://example.com\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">https://example.com</a>&lt;/b&gt;</p>",
            composed.contentHtml,
        )
    }

    @Test
    fun URLの途中のアポストロフィはリンクに含め囲みの引用符は外す() {
        val composed = assertIs<NoteComposer.ComposeResult.Composed>(
            NoteComposer.compose("https://en.wikipedia.org/wiki/I'm_Feeling_Lucky 'https://example.com'"),
        )

        assertEquals(
            "<p><a href=\"https://en.wikipedia.org/wiki/I'm_Feeling_Lucky\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">https://en.wikipedia.org/wiki/I'm_Feeling_Lucky</a> " +
                "'<a href=\"https://example.com\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">https://example.com</a>'</p>",
            composed.contentHtml,
        )
    }

    @Test
    fun 全角の閉じ括弧の後に文章が続いてもURLをそこで区切る() {
        val composed = assertIs<NoteComposer.ComposeResult.Composed>(
            NoteComposer.compose("こちら（https://example.com）を参照 https://ja.wikipedia.org/wiki/日本"),
        )

        assertEquals(
            "<p>こちら（<a href=\"https://example.com\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">https://example.com</a>）を参照 " +
                "<a href=\"https://ja.wikipedia.org/wiki/日本\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">https://ja.wikipedia.org/wiki/日本</a></p>",
            composed.contentHtml,
        )
    }
}
