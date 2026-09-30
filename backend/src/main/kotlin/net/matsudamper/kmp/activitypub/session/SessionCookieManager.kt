package net.matsudamper.kmp.activitypub.session

import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.util.date.GMTDate

/**
 * セッション Cookie の読み書き。
 * 発行と失効で属性がずれると別の Cookie として扱われるので、1 箇所にまとめる
 */
class SessionCookieManager(
    private val call: ApplicationCall,
    private val secure: Boolean,
    private val cookieName: String,
) {
    fun token(): String? = call.request.cookies[cookieName, ENCODING]

    fun append(
        token: String,
        maxAgeSeconds: Long,
    ) {
        append(value = token, maxAge = maxAgeSeconds, expires = null)
    }

    fun expire() {
        append(value = "", maxAge = null, expires = GMTDate.START)
    }

    private fun append(
        value: String,
        maxAge: Long?,
        expires: GMTDate?,
    ) {
        call.response.cookies.append(
            name = cookieName,
            value = value,
            encoding = ENCODING,
            maxAge = maxAge,
            expires = expires,
            path = "/",
            secure = secure,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        )
    }

    companion object {
        const val ADMIN_COOKIE_NAME: String = "admin_session"

        const val USER_COOKIE_NAME: String = "user_session"

        private val ENCODING = CookieEncoding.RAW
    }
}
