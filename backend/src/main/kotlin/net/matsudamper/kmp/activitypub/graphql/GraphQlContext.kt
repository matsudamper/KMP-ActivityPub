package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.atomic.AtomicBoolean
import io.ktor.server.application.ApplicationCall
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.session.UserSessionService
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

    private val loginAttempted = AtomicBoolean(false)

    /**
     * alias で login を並べてもハッシュ計算は 1 リクエスト 1 回までにする
     */
    fun claimLoginAttempt(): Boolean = loginAttempted.compareAndSet(false, true)

    private val userAccountId: AccountId? by lazy { userCookie.token()?.let { userSessions.accountIdOf(it) } }

    fun userAccountId(): AccountId? = userAccountId

    /**
     * 発行した Cookie はまだリクエストに無いので、[userAccountId] は変わらない
     */
    fun issueUserSession(accountId: AccountId) {
        userCookie.append(token = userSessions.create(accountId), maxAgeSeconds = userSessions.ttlSeconds)
    }
}
