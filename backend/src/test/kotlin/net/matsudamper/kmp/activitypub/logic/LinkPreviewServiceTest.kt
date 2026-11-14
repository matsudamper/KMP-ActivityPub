package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

class LinkPreviewServiceTest {
    @Test
    fun メンションとハッシュタグはカードにしない() {
        val service = service(pages = mapOf())
        val html = "<p>" +
            "<span class=\"h-card\"><a href=\"https://remote.example/@alice\" class=\"u-url mention\">@alice</a></span> " +
            "<a href=\"https://remote.example/tags/kotlin\" class=\"mention hashtag\" rel=\"tag\">#kotlin</a> " +
            "<a href=\"https://example.com/article\" rel=\"nofollow noopener\">https://example.com/article</a> " +
            "<a href=\"https://example.com/article\">dup</a> " +
            "<a href=\"mailto:alice@example.com\">mail</a>" +
            "</p>"

        assertEquals(listOf("https://example.com/article"), service.links(html))
    }

    @Test
    fun 受け取って保存した投稿でもメンションとハッシュタグはカードにしない() {
        val service = service(pages = mapOf())
        val received = "<p>" +
            "<span class=\"h-card\"><a href=\"https://remote.example/@alice\" class=\"u-url mention\">@<span>alice</span></a></span> " +
            "<a href=\"https://remote.example/tags/kotlin\" class=\"mention hashtag\" rel=\"tag\">#<span>kotlin</span></a> " +
            "<a href=\"https://example.com/article\" rel=\"nofollow noopener\">https://example.com/article</a>" +
            "</p>"
        // 受け取った投稿は保存前にこの形で掃除される
        val stored = Jsoup.clean(received, Safelist.basic())

        assertEquals(listOf("https://example.com/article"), service.links(stored))
    }

    @Test
    fun 以前このサーバーで書いた投稿の文字のままのURLもカードにする() {
        val service = service(pages = mapOf())
        val storedBeforeLinkify = "<p>記事 https://example.com/article.</p><p>（https://example.com/other）</p>"

        assertEquals(
            listOf("https://example.com/article", "https://example.com/other"),
            service.links(storedBeforeLinkify),
        )
    }

    @Test
    fun このサーバーで書いた投稿のURLをカードにする() {
        val service = service(pages = mapOf())
        val composed = NoteComposer.compose(
            "記事 https://example.com/article.\n\n（https://example.com/other）\n\n" +
                "(https://en.wikipedia.org/wiki/Kotlin_(programming_language))",
            mentions = listOf(),
        )
        val contentHtml = (composed as NoteComposer.ComposeResult.Composed).contentHtml

        assertEquals(
            listOf(
                "https://example.com/article",
                "https://example.com/other",
                "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
            ),
            service.links(contentHtml),
        )
    }

    @Test
    fun OGPを取り画像は中継URLにする() = runBlocking {
        val page = """
            <html><head>
            <title>fallback</title>
            <meta property="og:title" content="記事のタイトル">
            <meta property="og:site_name" content="Example">
            <meta property="og:image" content="/ogp.png">
            <link rel="icon" type="image/svg+xml" href="/icon.svg">
            <link rel="shortcut icon" href="/icon.png">
            </head></html>
        """.trimIndent()
        val service = service(pages = mapOf("https://example.com/article" to page))

        val previews = service.previews("<p><a href=\"https://example.com/article\">link</a></p>")

        assertEquals(
            listOf(
                LinkPreviewService.LinkPreview(
                    url = "https://example.com/article",
                    title = "記事のタイトル",
                    siteName = "Example",
                    imageUrl = "proxy:https://example.com/ogp.png",
                    faviconUrl = "proxy:https://example.com/icon.png",
                ),
            ),
            previews,
        )
    }

    @Test
    fun OGPが無ければtitleを使う() = runBlocking {
        val service = service(pages = mapOf("https://example.com/" to "<html><head><title> ページ </title></head></html>"))

        val preview = service.previews("<a href=\"https://example.com/\">link</a>").single()

        assertEquals("ページ", preview.title)
        assertEquals(null, preview.imageUrl)
        assertEquals("proxy:https://example.com/favicon.ico", preview.faviconUrl)
    }

    @Test
    fun 取れなかったリンクもURLだけ返す() = runBlocking {
        val service = service(pages = mapOf())

        val preview = service.previews("<a href=\"https://example.com/missing\">link</a>").single()

        assertEquals(LinkPreviewService.LinkPreview(url = "https://example.com/missing", title = null, siteName = null, imageUrl = null, faviconUrl = null), preview)
    }

    @Test
    fun 同じリンクは取り直さない() = runBlocking {
        val fetchedUrls = mutableListOf<String>()
        val service = LinkPreviewService(
            fetcher = { url ->
                fetchedUrls += url
                null
            },
            imageProxyUrlOf = { it },
            clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val html = "<a href=\"https://example.com/\">link</a>"

        service.previews(html)
        service.previews(html)

        assertEquals(listOf("https://example.com/"), fetchedUrls)
    }

    private fun service(pages: Map<String, String>): LinkPreviewService =
        LinkPreviewService(
            fetcher = { url -> pages[url]?.let { Jsoup.parse(it, url) } },
            imageProxyUrlOf = { "proxy:$it" },
            clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC),
        )
}
