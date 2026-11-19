package net.matsudamper.kmp.activitypub.staticfiles

import net.matsudamper.kmp.activitypub.logic.PageOgp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 画面の入口の HTML に、他のサービスに貼ったときのカードの meta を足す。
 * カードを作る側は JavaScript を動かさないので、配信する HTML に最初から書いておく
 */
internal object PageOgpHtml {
    fun inject(indexHtml: String, ogp: PageOgp): String {
        val document = Jsoup.parse(indexHtml)
        document.title(ogp.title)
        val head = document.head()
        head.appendMeta(property = "og:type", content = ogp.type.ogType)
        head.appendMeta(property = "og:title", content = ogp.title)
        head.appendMeta(property = "og:url", content = ogp.url)
        if (ogp.description != null) {
            head.appendMeta(property = "og:description", content = ogp.description)
            head.appendElement("meta").attr("name", "description").attr("content", ogp.description)
        }
        val image = ogp.image
        if (image != null) {
            head.appendMeta(property = "og:image", content = image.url)
            if (image.alt != null) {
                head.appendMeta(property = "og:image:alt", content = image.alt)
            }
        }
        val twitterCard = if (image?.large == true) "summary_large_image" else "summary"
        head.appendElement("meta").attr("name", "twitter:card").attr("content", twitterCard)
        return document.outerHtml()
    }

    private fun Element.appendMeta(property: String, content: String) {
        appendElement("meta").attr("property", property).attr("content", content)
    }
}
