package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.atomic.AtomicBoolean
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import net.matsudamper.kmp.activitypub.admin.AdminSessionInMemoryStore
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.shared.AccountId

class GraphQlContext(
    call: ApplicationCall,
    private val adminSessionStore: AdminSessionInMemoryStore,
    private val sessionService: UserSessionService,
    cookieSecure: Boolean,
    trustCfConnectingIp: Boolean,
) {
    private val adminCookie = SessionCookieManager(
        call = call,
        secure = cookieSecure,
        cookieName = SessionCookieManager.ADMIN_COOKIE_NAME,
    )

    private val userCookie = SessionCookieManager(
        call = call,
        secure = cookieSecure,
        cookieName = SessionCookieManager.USER_COOKIE_NAME,
    )

    /**
     * trustCfConnectingIp が無いままリバースプロキシの後ろに置くとプロキシの IP になり、全員がまとめて締め出される
     */
    val clientIp: String =
        call.request.headers["CF-Connecting-IP"]?.takeIf { trustCfConnectingIp }
            ?: call.request.origin.remoteAddress

    private val loginAttempted = AtomicBoolean(false)

    /**
     * alias で login を並べてもハッシュ計算は 1 リクエスト 1 回までにする
     */
    fun claimLoginAttempt(): Boolean = loginAttempted.compareAndSet(false, true)

    fun isAdminLoggedIn(): Boolean = adminSessionStore.isValid(adminCookie.token())

    /**
     * 発行した Cookie はまだリクエスト側に無いので、[isAdminLoggedIn] は false のまま
     */
    fun issueAdminSession() {
        adminCookie.append(token = adminSessionStore.create(), maxAgeSeconds = adminSessionStore.ttlSeconds)
    }

    fun clearAdminSession() {
        adminSessionStore.remove(adminCookie.token())
        adminCookie.expire()
    }

    fun userAccountId(): AccountId? {
        val token = userCookie.token() ?: return null
        return sessionService.accountIdOf(token)
    }

    /**
     * 発行した Cookie はまだリクエスト側に無いので、[userAccountId] は null のまま
     */
    fun issueUserSession(accountId: AccountId) {
        userCookie.append(token = sessionService.create(accountId), maxAgeSeconds = sessionService.ttlSeconds)
    }

    fun clearUserSession() {
        userCookie.token()?.let { sessionService.delete(it) }
        userCookie.expire()
    }
}
