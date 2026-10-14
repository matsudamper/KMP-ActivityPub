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
    private val byHost = ConcurrentHashMap<String, MutableSet<String>>()
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        ensureLoaded()
        val merged =
            (cookiesForHost(url) + cookies)
                .associateBy { it.name }
                .values
                .filter { it.expiresAt > System.currentTimeMillis() }

        val hostCookies = merged.map { it.toString() }.toMutableSet()
        if (hostCookies.isEmpty()) {
            byHost.remove(url.host)
        } else {
            byHost[url.host] = hostCookies
        }
        blockStore.writeAsync(byHost.mapValues { (_, cookieSet) -> cookieSet.toSet() })
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookiesForHost(url).filter { cookie ->
            cookie.expiresAt > System.currentTimeMillis() && cookie.matches(url)
        }

    private fun cookiesForHost(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        return byHost[url.host].orEmpty().mapNotNull { serialized -> Cookie.parse(url, serialized) }
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(loadLock) {
            if (loaded) {
                return
            }
            byHost.clear()
            blockStore.read().forEach { (host, cookies) ->
                byHost[host] = cookies.toMutableSet()
            }
            loaded = true
        }
    }
}
