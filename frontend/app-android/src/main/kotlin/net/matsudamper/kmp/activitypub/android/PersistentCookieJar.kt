package net.matsudamper.kmp.activitypub.android

import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * アプリを開くたびにログインし直さずに済むよう、Cookie を残す
 */
internal class PersistentCookieJar(
    private val blockStore: CookieBlockStore,
) : CookieJar {
    private val entries = CopyOnWriteArrayList<StoredCookieEntry>()
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val merged = LinkedHashMap<String, Pair<HttpUrl, Cookie>>()

        for (entry in entries) {
            val originUrl = entry.parseOrigin() ?: continue
            val cookie = Cookie.parse(originUrl, entry.value) ?: continue
            merged[cookie.identityKey()] = originUrl to cookie
        }
        for (cookie in cookies) {
            merged[cookie.identityKey()] = url to cookie
        }

        val nextEntries =
            merged.values
                .filter { (_, cookie) -> cookie.expiresAt > now }
                .map { (originUrl, cookie) ->
                    StoredCookieEntry(origin = originUrl.persistedOrigin(), value = cookie.toString())
                }

        entries.clear()
        entries.addAll(nextEntries)
        blockStore.writeEntriesAsync(entries.toList())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        val now = System.currentTimeMillis()
        return entries
            .mapNotNull { entry ->
                val originUrl = entry.parseOrigin() ?: return@mapNotNull null
                Cookie.parse(originUrl, entry.value)
            }.filter { cookie -> cookie.expiresAt > now && cookie.matches(url) }
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(loadLock) {
            if (loaded) {
                return
            }
            entries.clear()
            entries.addAll(blockStore.readEntries())
            loaded = true
        }
    }

    private fun StoredCookieEntry.parseOrigin(): HttpUrl? =
        runCatching { origin.toHttpUrl() }.getOrNull()

    private fun HttpUrl.persistedOrigin(): String {
        val defaultPort = if (scheme == "https") 443 else 80
        return if (port == defaultPort) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }
    }

    private fun Cookie.identityKey(): String = "$name|$domain|$path"
}
