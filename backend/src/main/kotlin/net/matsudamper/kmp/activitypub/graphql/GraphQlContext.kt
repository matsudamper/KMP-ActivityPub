package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.atomic.AtomicBoolean
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

    private val loginAttempted = AtomicBoolean(false)

    /**
     * alias で 1 つのリクエストに login を並べると、その数だけ PBKDF2 が回る。
     * 照合は 1 リクエストにつき 1 回までにする
     *
     * @return このリクエストで初めてなら true
     */
    fun claimLoginAttempt(): Boolean = loginAttempted.compareAndSet(false, true)

    /**
     * alias で session を並べられても DB を引くのは 1 回にする
     */
    private val userAccountId: AccountId? by lazy { userCookie.token()?.let { userSessions.accountIdOf(it) } }

    fun userAccountId(): AccountId? = userAccountId

    /**
     * 発行した Cookie はまだリクエスト側に無いので、[userAccountId] は null のまま
     */
    fun issueUserSession(accountId: AccountId) {
        userCookie.append(token = userSessions.create(accountId), maxAgeSeconds = userSessions.ttlSeconds)
    }
}
