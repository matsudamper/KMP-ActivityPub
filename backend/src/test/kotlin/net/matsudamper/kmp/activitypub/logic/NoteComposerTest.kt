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
}
