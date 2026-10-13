package net.matsudamper.kmp.activitypub.android

import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * アプリを開くたびにログインし直さずに済むよう、Cookie を残す
 */
internal class PersistentCookieJar(
    private val preferences: SharedPreferences,
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val merged = (loadAll(url) + cookies)
            .associateBy { it.name }
            .values
            .filter { it.expiresAt > System.currentTimeMillis() }

        preferences.edit {
            putStringSet(url.host, merged.map { it.toString() }.toSet())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        loadAll(url).filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }

    private fun loadAll(url: HttpUrl): List<Cookie> =
        preferences.getStringSet(url.host, setOf()).orEmpty().mapNotNull { Cookie.parse(url, it) }
}
