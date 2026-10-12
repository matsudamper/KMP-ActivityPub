package net.matsudamper.kmp.activitypub.graphql

import io.ktor.server.application.ApplicationCall
import net.matsudamper.kmp.activitypub.logic.UserSessionService
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.shared.AccountId

class GraphQlContext(
    call: ApplicationCall,
    private val userSessions: UserSessionService,
    cookieSecure: Boolean,
) {
    private val userCookie = SessionCookieManager(
        call = call,
        secure = cookieSecure,
        cookieName = SessionCookieManager.USER_COOKIE_NAME,
    )

    fun userAccountId(): AccountId? = userCookie.token()?.let { userSessions.accountIdOf(it) }

    /**
     * 発行した Cookie はまだリクエスト側に無いので、[userAccountId] は null のまま
     */
    fun issueUserSession(accountId: AccountId) {
        userCookie.append(token = userSessions.create(accountId), maxAgeSeconds = userSessions.ttlSeconds)
    }
}
