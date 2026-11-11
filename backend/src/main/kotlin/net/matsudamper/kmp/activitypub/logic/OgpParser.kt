package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 投稿の本文からカードにするリンクを、リンク先の HTML から OGP を抜き出す。
 */
internal object OgpParser {
    private val FAVICON_RELS = setOf("icon", "apple-touch-icon")

    /**
     * 本文のリンクを出てくる順に、重複を除いて返す。http と https のものだけ。
     *
     * 以前このサーバーで書いた投稿は URL を `<a>` にせず文字のまま保存しているので、`<a>` の外にある URL も拾う。
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
                        PlainTextUrls.find(node.wholeText).forEach { add(node.wholeText.substring(it)) }
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
            ?.takeIf { it.isHttpUrl() }

        return Ogp(
            title = title,
            siteName = metas["og:site_name"]?.attr("content")?.trim(),
            imageUrl = imageUrl,
            faviconUrl = faviconUrl(document),
        )
    }

    /**
     * `<link rel="icon">` が無いサイトもブラウザが /favicon.ico を見に行くので、それに倣う。
     * SVG は中継で配れないので選ばない
     */
    private fun faviconUrl(document: Document): String? {
        val declared = document.select("link[rel][href]")
            .filterNot { link -> link.attr("type").equals("image/svg+xml", ignoreCase = true) || link.attr("href").substringBefore('?').endsWith(".svg", ignoreCase = true) }
            .firstOrNull { link -> link.attr("rel").lowercase().split(' ').any { it in FAVICON_RELS } }
            ?.absUrl("href")
            ?.takeIf { it.isHttpUrl() }
        if (declared != null) return declared

        val location = document.location()
        if (!location.isHttpUrl()) return null
        return runCatching { URI(location).resolve("/favicon.ico").toString() }.getOrNull()
    }

    private fun String.isHttpUrl(): Boolean = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun Element.isMentionOrHashtag(): Boolean =
        hasClass("mention") ||
            hasClass("hashtag") ||
            attr("rel").split(' ').any { it.equals("tag", ignoreCase = true) } ||
            text().trimStart().let { it.startsWith("@") || it.startsWith("#") }

    data class Ogp(
        val title: String?,
        val siteName: String?,
        val imageUrl: String?,
        val faviconUrl: String?,
    )
}
