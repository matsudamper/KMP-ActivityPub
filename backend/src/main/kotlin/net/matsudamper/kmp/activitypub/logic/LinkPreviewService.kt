package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Document

/**
 * 投稿の本文にあるリンク先の OGP を取ってきて、カードに出す内容にする。
 *
 * 画面（ブラウザ）からは CORS に阻まれて他のサイトの HTML を読めないので、こちらで取る。
 * 取りに行く先は画面から見えている投稿の本文にあるリンクだけで、画面から任意の URL は渡せない。
 *
 * @param imageProxyUrlOf OGP 画像を画面へ渡す URL にする。画面から取得元へ直接取りに行くと、
 *   Web では CORS を返さないサイトの画像を読めない。中継できなければ null
 */
class LinkPreviewService(
    private val fetcher: LinkPreviewPageFetcher,
    private val imageProxyUrlOf: (imageUrl: String) -> String?,
    private val clock: Clock,
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val fetchPermits = Semaphore(MAX_CONCURRENT_FETCHES)

    /**
     * カードにするリンク。[previews] はこの並びで返す
     */
    fun links(contentHtml: String): List<String> = OgpParser.links(contentHtml).take(MAX_LINKS_PER_NOTE)

    /**
     * 本文のリンクごとに 1 件返す。取れなかったリンクも URL だけ入れて返す
     */
    suspend fun previews(contentHtml: String): List<LinkPreview> {
        val links = links(contentHtml)
        return coroutineScope {
            links.map { url -> async { preview(url) } }.awaitAll()
        }
    }

    private suspend fun preview(url: String): LinkPreview {
        val now = clock.instant()
        val cached = cache[url]
        if (cached != null && cached.expiresAt.isAfter(now)) return cached.preview

        val ogp = fetchPermits.withPermit { fetcher.fetch(url) }?.let { OgpParser.parse(it) }
        val preview = LinkPreview(
            url = url,
            title = ogp?.title,
            siteName = ogp?.siteName,
            imageUrl = ogp?.imageUrl?.let(imageProxyUrlOf),
        )
        store(
            url = url,
            entry = CacheEntry(
                preview = preview,
                expiresAt = now.plus((if (ogp == null) FAILURE_TTL else SUCCESS_TTL).toJavaDuration()),
            ),
            now = now,
        )
        return preview
    }

    private fun store(
        url: String,
        entry: CacheEntry,
        now: Instant,
    ) {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.entries.removeIf { !it.value.expiresAt.isAfter(now) }
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        }
        cache[url] = entry
    }

    /**
     * 取れなかった項目は null
     */
    data class LinkPreview(
        val url: String,
        val title: String?,
        val siteName: String?,
        val imageUrl: String?,
    )

    private data class CacheEntry(
        val preview: LinkPreview,
        val expiresAt: Instant,
    )

    private companion object {
        const val MAX_LINKS_PER_NOTE = 4
        const val MAX_CONCURRENT_FETCHES = 4
        const val MAX_CACHE_ENTRIES = 2000
        val SUCCESS_TTL: Duration = 24.hours

        /**
         * 取れなかったものも覚えておく。覚えないと、タイムラインを開くたびに同じ相手へ取りに行く
         */
        val FAILURE_TTL: Duration = 1.hours
    }
}

fun interface LinkPreviewPageFetcher {
    /**
     * リンク先の HTML。baseUri はリダイレクトを辿った後の URL にする。HTML として取れなければ null
     */
    suspend fun fetch(url: String): Document?
}
