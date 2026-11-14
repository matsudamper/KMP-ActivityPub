package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class PostMentionsTest {
    @Test
    fun Mastodonのメンションはリンク先のサーバーで宛先を作る() {
        val html =
            "<p><span class=\"h-card\"><a href=\"https://mastodon.example/@alice\" class=\"u-url mention\">@<span>alice</span></a></span> こんにちは</p>"

        assertEquals(listOf(PostMention(text = "@alice", acct = "alice@mastodon.example")), PostMentions.find(html))
    }

    @Test
    fun サーバーまで書かれたメンションは書かれたサーバーを使う() {
        val html = "<p><a href=\"https://misskey.example/@bob\">@bob@misskey.example</a></p>"

        assertEquals(listOf(PostMention(text = "@bob@misskey.example", acct = "bob@misskey.example")), PostMentions.find(html))
    }

    @Test
    fun ハッシュタグとURLのリンクは拾わない() {
        val html = "<p><a href=\"https://mastodon.example/tags/kotlin\">#kotlin</a> <a href=\"https://kotlinlang.org/\">https://kotlinlang.org/</a></p>"

        assertEquals(listOf(), PostMentions.find(html))
    }

    @Test
    fun 同じ相手へのメンションは1つにまとめる() {
        val html = "<p><a href=\"https://mastodon.example/@alice\">@alice</a> <a href=\"https://mastodon.example/@alice\">@alice</a></p>"

        assertEquals(listOf(PostMention(text = "@alice", acct = "alice@mastodon.example")), PostMentions.find(html))
    }
}
