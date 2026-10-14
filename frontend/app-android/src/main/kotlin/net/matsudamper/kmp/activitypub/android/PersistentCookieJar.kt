package net.matsudamper.kmp.activitypub.android

import net.matsudamper.kmp.activitypub.shared.UserSessionCookie
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 起動中の Cookie はメモリに置く。Block Store へ書くのはセッション Cookie が変わったときだけ
 */
internal class PersistentCookieJar(
    private val blockStore: CookieBlockStore,
) : CookieJar {
    private val cookieLock = Any()
    private val entries = mutableListOf<StoredCookieEntry>()
    private var persistedSession: StoredCookieEntry? = null

    @Volatile
    private var loaded = false

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        synchronized(cookieLock) {
            ensureLoadedWithinLock()
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

            val session =
                merged.values
                    .firstOrNull { (_, cookie) ->
                        cookie.name == UserSessionCookie.NAME && cookie.expiresAt > now
                    }?.let { (originUrl, cookie) ->
                        StoredCookieEntry(origin = originUrl.persistedOrigin(), value = cookie.toString())
                    }
            if (session != persistedSession) {
                persistedSession = session
                blockStore.writeSessionAsync(session)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(cookieLock) {
            ensureLoadedWithinLock()
            val now = System.currentTimeMillis()
            return entries
                .mapNotNull { entry ->
                    val originUrl = entry.parseOrigin() ?: return@mapNotNull null
                    Cookie.parse(originUrl, entry.value)
                }.filter { cookie -> cookie.expiresAt > now && cookie.matches(url) }
        }
    }

    private fun ensureLoadedWithinLock() {
        if (loaded) {
            return
        }
        val session = blockStore.readSession()
        if (session != null) {
            entries.add(session)
        }
        persistedSession = session
        loaded = true
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
