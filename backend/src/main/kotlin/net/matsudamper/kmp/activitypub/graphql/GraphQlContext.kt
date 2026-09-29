package net.matsudamper.kmp.activitypub.graphql

import io.ktor.server.application.ApplicationCall
import net.matsudamper.kmp.activitypub.admin.AdminSessionInMemoryStore
import net.matsudamper.kmp.activitypub.logic.UserSessionService
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 通信に関係するものを入れておく
 */
class GraphQlContext(
    call: ApplicationCall,
    private val adminSessionStore: AdminSessionInMemoryStore,
    private val userSessions: UserSessionService,
    cookieSecure: Boolean,
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

    fun isAdminLoggedIn(): Boolean = adminSessionStore.isValid(adminCookie.token())

    /**
     * Cookieを発行する
     * 発行した Cookie はまだリクエスト側に無いので、[isAdminLoggedIn] は false のまま
     */
    fun issueAdminSession() {
        adminCookie.append(token = adminSessionStore.create(), maxAgeSeconds = adminSessionStore.ttlSeconds)
    }

    fun clearAdminSession() {
        adminSessionStore.remove(adminCookie.token())
        adminCookie.expire()
    }

    /**
     * ログイン中のユーザー。ログインしていなければ null
     */
    fun userAccountId(): AccountId? = userCookie.token()?.let { userSessions.accountIdOf(it) }

    /**
     * 発行した Cookie はまだリクエスト側に無いので、[userAccountId] は null のまま
     */
    fun issueUserSession(accountId: AccountId) {
        userCookie.append(token = userSessions.create(accountId), maxAgeSeconds = userSessions.ttlSeconds)
    }

    fun clearUserSession() {
        userCookie.token()?.let { userSessions.delete(it) }
        userCookie.expire()
    }
}
