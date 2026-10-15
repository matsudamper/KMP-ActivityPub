package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.atomic.AtomicBoolean
import graphql.schema.DataFetchingEnvironment
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.renderSetCookieHeader
import io.ktor.server.application.ApplicationCall

internal class GraphQlContext {
    private val dummySessionCookieRequested = AtomicBoolean(false)

    fun requestDummySessionCookie() {
        dummySessionCookieRequested.set(true)
    }

    fun writeCookies(call: ApplicationCall) {
        if (!dummySessionCookieRequested.get()) return
        call.response.headers.append(
            name = HttpHeaders.SetCookie,
            value = renderSetCookieHeader(
                name = USER_SESSION_COOKIE_NAME,
                value = DUMMY_USER_SESSION,
                encoding = CookieEncoding.RAW,
                path = "/",
                httpOnly = true,
                extensions = mapOf("SameSite" to "Strict"),
                includeEncoding = false,
            ),
        )
    }

    companion object {
        internal val KEY = Any()

        /**
         * クライアントがこの名前の Cookie だけを保存する
         */
        const val USER_SESSION_COOKIE_NAME: String = "user_session"

        const val DUMMY_USER_SESSION: String = "dummy"

        fun from(env: DataFetchingEnvironment): GraphQlContext {
            return requireNotNull(env.graphQlContext.get(KEY)) {
                "GraphQLContext に GraphQlContext が無い"
            }
        }
    }
}
