package net.matsudamper.kmp.activitypub.staticfiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.logic.PageOgp
import net.matsudamper.kmp.activitypub.logic.PageOgpImage
import net.matsudamper.kmp.activitypub.logic.PageOgpType
import org.jsoup.Jsoup

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class PageOgpHtmlTest {
    private val indexHtml = "<html><head><title>kmp-activitypub</title></head><body><script src=\"/frontend.js\"></script></body></html>"

    @Test
    fun `カードの meta を足し、本文の記号はエスケープする`() {
        val ogp = PageOgp(
            type = PageOgpType.Article,
            title = "alice (@alice@example.com)",
            description = "\"><script>alert(1)</script>",
            url = "https://example.com/posts/l1",
            image = PageOgpImage(url = "https://example.com/media/img1", alt = "猫", large = true),
        )

        val html = PageOgpHtml.inject(indexHtml = indexHtml, ogp = ogp)
        val document = Jsoup.parse(html)

        assertEquals("alice (@alice@example.com)", document.title())
        assertEquals("\"><script>alert(1)</script>", document.selectFirst("meta[property=og:description]")?.attr("content"))
        assertEquals("https://example.com/media/img1", document.selectFirst("meta[property=og:image]")?.attr("content"))
        assertEquals("summary_large_image", document.selectFirst("meta[name=twitter:card]")?.attr("content"))
        assertEquals(1, document.select("script").size)
        assertTrue(document.select("head script").isEmpty())
    }

    @Test
    fun `画像が無ければ og image を出さず小さいカードにする`() {
        val ogp = PageOgp(type = PageOgpType.Profile, title = "alice", description = null, url = "https://example.com/@alice", image = null)

        val document = Jsoup.parse(PageOgpHtml.inject(indexHtml = indexHtml, ogp = ogp))

        assertTrue(document.select("meta[property=og:image]").isEmpty())
        assertTrue(document.select("meta[property=og:description]").isEmpty())
        assertEquals("summary", document.selectFirst("meta[name=twitter:card]")?.attr("content"))
    }
}
