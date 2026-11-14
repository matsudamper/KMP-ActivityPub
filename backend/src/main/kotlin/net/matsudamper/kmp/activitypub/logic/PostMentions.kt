package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.net.URISyntaxException
import org.jsoup.nodes.Element

/**
 * 本文の HTML からメンションのリンクを拾う。
 * 受け取った投稿は保存前に Safelist.basic で `class` が落ちるので、`mention` の class ではなく表示が `@名前` かで見分ける
 */
internal object PostMentions {
    private val MENTION_TEXT = Regex("""^@([\w.-]+)(?:@([\w.-]+))?$""")

    /**
     * 本文に出てくる順に返す
     */
    fun find(document: Element): List<MentionAnchor> =
        document.select("a[href]").mapNotNull { element ->
            element.acct()?.let { acct -> MentionAnchor(element = element, acct = acct) }
        }

    /**
     * Mastodon は `@alice` とだけ書くので、サーバーはリンク先から取る
     */
    private fun Element.acct(): String? {
        val match = MENTION_TEXT.matchEntire(text().trim()) ?: return null
        val username = match.groupValues[1]
        val host = match.groupValues[2].ifEmpty { hrefHost() } ?: return null
        return "$username@$host"
    }

    private fun Element.hrefHost(): String? =
        try {
            URI(attr("href").trim()).host
        } catch (_: URISyntaxException) {
            null
        }

    /**
     * @param acct プロフィールを開くときの `user@host`
     */
    class MentionAnchor(
        val element: Element,
        val acct: String,
    )
}
