package net.matsudamper.kmp.activitypub.logic

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 投稿の本文からカードにするリンクを、リンク先の HTML から OGP を抜き出す。
 */
internal object OgpParser {
    /**
     * 本文の `<a href>` を出てくる順に、重複を除いて返す。http と https のものだけ。
     *
     * Mastodon などはメンションとハッシュタグもリンクにして `class="mention"` / `class="hashtag"` や
     * `rel="tag"` を付ける。これらはプロフィールやタグの一覧を指すだけなのでカードにしない
     */
    fun links(contentHtml: String): List<String> =
        Jsoup.parseBodyFragment(contentHtml).select("a[href]")
            .asSequence()
            .filterNot { it.isMentionOrHashtag() }
            .map { it.attr("href").trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()
            .toList()

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
        hasClass("mention") || hasClass("hashtag") || attr("rel").split(' ').any { it.equals("tag", ignoreCase = true) }

    data class Ogp(
        val title: String?,
        val siteName: String?,
        val imageUrl: String?,
    )
}
