package net.matsudamper.kmp.activitypub.android.session

import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal data class SessionCookie(
    val origin: String,
    val cookie: String,
) {
    fun forRequest(url: HttpUrl, now: Long): Cookie? {
        if (!url.isHttps || origin != originOf(url)) return null
        val parsed = parse() ?: return null
        return parsed.takeIf { it.expiresAt > now && it.matches(url) }
    }

    fun parse(): Cookie? {
        val url = origin.toHttpUrlOrNull() ?: return null
        if (!url.isHttps || origin != originOf(url)) return null
        return Cookie.parse(url, cookie)?.takeIf { it.name == NAME && it.matches(url.newBuilder().encodedPath("/graphql").build()) }
    }

    companion object {
        const val NAME = "user_session"

        fun originOf(url: HttpUrl): String = url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()

        fun fromResponse(url: HttpUrl, value: String): SessionCookie? {
            if (!url.isHttps) return null
            val parsed = Cookie.parse(url, value) ?: return null
            if (parsed.name != NAME || !parsed.matches(url)) return null
            return SessionCookie(origin = originOf(url), cookie = parsed.toString())
        }
    }
}

@Serializable
internal data class SessionState(
    val session: SessionCookie?,
    val backupPending: Boolean,
)
