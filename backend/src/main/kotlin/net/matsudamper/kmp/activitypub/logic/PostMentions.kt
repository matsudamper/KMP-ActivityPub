package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.net.URISyntaxException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 本文の HTML からメンションのリンクを拾う。
 * 受け取った投稿は保存前に Safelist.basic で `class` が落ちるので、`mention` の class ではなく表示が `@名前` かで見分ける
 */
internal object PostMentions {
    private val MENTION_TEXT = Regex("""^@([\w.-]+)(?:@([\w.-]+))?$""")

    fun find(contentHtml: String): List<PostMention> =
        Jsoup.parseBodyFragment(contentHtml).select("a[href]")
            .mapNotNull { it.toPostMention() }
            .distinct()

    /**
     * Mastodon は `@alice` とだけ書くので、サーバーはリンク先から取る
     */
    private fun Element.toPostMention(): PostMention? {
        val text = text().trim()
        val match = MENTION_TEXT.matchEntire(text) ?: return null
        val username = match.groupValues[1]
        val host = match.groupValues[2].ifEmpty { hrefHost() } ?: return null
        return PostMention(text = text, acct = "$username@$host")
    }

    private fun Element.hrefHost(): String? =
        try {
            URI(attr("href").trim()).host
        } catch (_: URISyntaxException) {
            null
        }
}

/**
 * @param text 平文にした本文での書かれ方。`@alice` や `@alice@example.com`
 * @param acct プロフィールを開くときの `user@host`
 */
internal data class PostMention(
    val text: String,
    val acct: String,
)
