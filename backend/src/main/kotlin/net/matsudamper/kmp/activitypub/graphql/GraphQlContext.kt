package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.atomic.AtomicBoolean
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.shared.AccountId

class GraphQlContext(
    call: ApplicationCall,
    private val sessionService: UserSessionService,
    cookieSecure: Boolean,
    trustCfConnectingIp: Boolean,
) {
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
    private val passkeyChallengeIssued = AtomicBoolean(false)

    /**
     * alias で login を並べてもハッシュ計算は 1 リクエスト 1 回までにする
     */
    fun claimLoginAttempt(): Boolean = loginAttempted.compareAndSet(false, true)

    /**
     * alias で並べて challenge の上限を一度に埋められないよう、1 リクエスト 1 回までにする
     */
    fun claimPasskeyChallenge(): Boolean = passkeyChallengeIssued.compareAndSet(false, true)

    fun userAccountId(): AccountId? {
        val token = userCookie.token() ?: return null
        return sessionService.accountIdOf(token)
    }

    fun issueUserSession(accountId: AccountId) {
        userCookie.append(token = sessionService.create(accountId), maxAgeSeconds = sessionService.ttlSeconds)
    }
}
