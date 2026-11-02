package net.matsudamper.kmp.activitypub.logic

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 投稿の本文からカードにするリンクを、リンク先の HTML から OGP を抜き出す。
 */
internal object OgpParser {
    private val plainTextUrl = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /**
     * 本文のリンクを出てくる順に、重複を除いて返す。http と https のものだけ。
     *
     * このサーバーで書いた投稿は URL を `<a>` にせず文字のまま保存しているので、`<a>` の外にある URL も拾う。
     *
     * Mastodon などはメンションとハッシュタグもリンクにする。これらはプロフィールやタグの一覧を指すだけなので
     * カードにしない。受け取った投稿は保存前に Safelist.basic で `class` / `rel` が落ちるので、
     * 表示が `@` / `#` で始まるかでも見分ける
     */
    fun links(contentHtml: String): List<String> =
        buildList {
            Jsoup.parseBodyFragment(contentHtml).body().traverse { node, _ ->
                when {
                    node is Element && node.normalName() == "a" && node.hasAttr("href") && !node.isMentionOrHashtag() ->
                        add(node.attr("href").trim())

                    node is TextNode && (node.parent() as? Element)?.closest("a") == null ->
                        plainTextUrl.findAll(node.wholeText).forEach { add(it.value.trimEnd(*TRAILING_PUNCTUATION)) }
                }
            }
        }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()

    /**
     * @param document リンク先の URL を baseUri にしておく。相対で書かれた画像の基準にする
     */
    fun parse(document: Document): Ogp {
        val metas = document.select("meta[content]")
            .mapNotNull { meta ->
                val key = meta.attr("property").ifEmpty { meta.attr("name") }.lowercase()
                val content = meta.attr("content").trim()
                if (key.isEmpty() || content.isEmpty()) null else key to meta
            }
            // 同じ property が複数あれば先頭を使う
            .distinctBy { it.first }
            .toMap()

        val title = metas["og:title"]?.attr("content")?.trim()
            ?: metas["twitter:title"]?.attr("content")?.trim()
            ?: document.title().trim().ifEmpty { null }
        val imageUrl = (metas["og:image:secure_url"] ?: metas["og:image"] ?: metas["twitter:image"])
            ?.absUrl("content")
            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }

        return Ogp(
            title = title,
            siteName = metas["og:site_name"]?.attr("content")?.trim(),
            imageUrl = imageUrl,
        )
    }

    private fun Element.isMentionOrHashtag(): Boolean =
        hasClass("mention") ||
            hasClass("hashtag") ||
            attr("rel").split(' ').any { it.equals("tag", ignoreCase = true) } ||
            text().trimStart().let { it.startsWith("@") || it.startsWith("#") }

    /**
     * 文末の句読点や閉じ括弧が URL に続けて書かれることが多いので、末尾からは外す
     */
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', '!', '?', ')', ']', ':', ';', '。', '、', '」', '）')

    data class Ogp(
        val title: String?,
        val siteName: String?,
        val imageUrl: String?,
    )
}
