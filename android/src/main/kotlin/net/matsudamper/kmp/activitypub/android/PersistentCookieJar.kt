package net.matsudamper.kmp.activitypub.android

import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * ログインのセッション Cookie をアプリを閉じても残す。
 *
 * 残さないと、アプリを開くたびにログインし直すことになる。
 * 1 行 1 Cookie の `Set-Cookie` の形で持ち、読むときに期限切れを落とす。
 */
internal class PersistentCookieJar(
    private val preferences: SharedPreferences,
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val merged = (loadAll(url) + cookies)
            // 同じ名前は後から来たもので置き換える。失効させる Set-Cookie もここで上書きする
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
