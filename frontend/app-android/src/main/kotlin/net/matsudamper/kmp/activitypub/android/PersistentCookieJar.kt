package net.matsudamper.kmp.activitypub.android

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * アプリを開くたびにログインし直さずに済むよう、Cookie を残す
 */
internal class PersistentCookieJar(
    private val blockStore: CookieBlockStore,
) : CookieJar {
    private val serializedCookies = ConcurrentHashMap.newKeySet<String>()
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        ensureLoaded()
        val merged =
            (parseAll(url) + cookies)
                .filter { cookie -> cookie.expiresAt > System.currentTimeMillis() }
                .associateBy { cookie -> cookie.identityKey() }
                .values

        serializedCookies.clear()
        serializedCookies.addAll(merged.map { cookie -> cookie.toString() })
        blockStore.writeAsync(serializedCookies.toSet())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        parseAll(url).filter { cookie ->
            cookie.expiresAt > System.currentTimeMillis() && cookie.matches(url)
        }

    private fun parseAll(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        return serializedCookies.mapNotNull { value -> Cookie.parse(url, value) }
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(loadLock) {
            if (loaded) {
                return
            }
            serializedCookies.clear()
            serializedCookies.addAll(blockStore.read())
            loaded = true
        }
    }

    private fun Cookie.identityKey(): String = "$name|$domain|$path"
}
