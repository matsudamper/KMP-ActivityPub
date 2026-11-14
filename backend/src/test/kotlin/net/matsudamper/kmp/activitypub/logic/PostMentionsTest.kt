package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class PostMentionsTest {
    @Test
    fun Mastodonのメンションはリンク先のサーバーで宛先を作る() {
        val html =
            "<p><span class=\"h-card\"><a href=\"https://mastodon.example/@alice\" class=\"u-url mention\">@<span>alice</span></a></span> こんにちは</p>"

        assertEquals(
            PlainTextPost(text = "@alice こんにちは", mentions = listOf(PlainTextMention(start = 0, end = 6, acct = "alice@mastodon.example"))),
            htmlToPlainTextWithMentions(html),
        )
    }

    @Test
    fun サーバーまで書かれたメンションは書かれたサーバーを使う() {
        val html = "<p>やあ <a href=\"https://misskey.example/@bob\">@bob@misskey.example</a></p>"

        assertEquals(
            PlainTextPost(text = "やあ @bob@misskey.example", mentions = listOf(PlainTextMention(start = 3, end = 23, acct = "bob@misskey.example"))),
            htmlToPlainTextWithMentions(html),
        )
    }

    @Test
    fun 同じ名前の別のサーバーの相手は位置ごとに宛先を分ける() {
        val html = "<p><a href=\"https://one.example/@alice\">@alice</a> と <a href=\"https://two.example/@alice\">@alice</a></p>"

        assertEquals(
            listOf(
                PlainTextMention(start = 0, end = 6, acct = "alice@one.example"),
                PlainTextMention(start = 9, end = 15, acct = "alice@two.example"),
            ),
            htmlToPlainTextWithMentions(html).mentions,
        )
    }

    @Test
    fun 段落と改行をまたいでも平文での位置を返す() {
        val html = "<p>first</p><p>line<br>\n  <a href=\"https://mastodon.example/@alice\">@alice</a></p>"

        val plainText = htmlToPlainTextWithMentions(html)

        assertEquals("first\n\nline\n@alice", plainText.text)
        assertEquals(listOf(PlainTextMention(start = 12, end = 18, acct = "alice@mastodon.example")), plainText.mentions)
    }

    @Test
    fun ハッシュタグとURLのリンクは拾わない() {
        val html = "<p><a href=\"https://mastodon.example/tags/kotlin\">#kotlin</a> <a href=\"https://kotlinlang.org/\">https://kotlinlang.org/</a></p>"

        assertEquals(listOf(), htmlToPlainTextWithMentions(html).mentions)
    }

    @Test
    fun 本文にある目印の文字は消してから位置を数える() {
        val html = "<p><a href=\"https://mastodon.example/@alice\">@alice</a></p>"

        assertEquals(
            PlainTextPost(text = "@alice", mentions = listOf(PlainTextMention(start = 0, end = 6, acct = "alice@mastodon.example"))),
            htmlToPlainTextWithMentions(html),
        )
    }
}
