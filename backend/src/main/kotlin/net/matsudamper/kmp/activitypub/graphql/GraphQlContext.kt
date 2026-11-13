package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.atomic.AtomicBoolean
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import net.matsudamper.kmp.activitypub.session.SessionCookieManager
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.shared.AccountId

class GraphQlContext(
    private val call: ApplicationCall,
    private val sessionService: UserSessionService,
    cookieSecure: Boolean,
    trustCfConnectingIp: Boolean,
) {
    /**
     * trustCfConnectingIp が無いままリバースプロキシの後ろに置くとプロキシの IP になり、全員がまとめて締め出される
     */
    val clientIp: String =
        call.request.headers["CF-Connecting-IP"]?.takeIf { trustCfConnectingIp }
            ?: call.request.origin.remoteAddress

    private val userCookie = SessionCookieManager(
        call = call,
        secure = cookieSecure,
        cookieName = SessionCookieManager.USER_COOKIE_NAME,
    )

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
        // WebSocket は接続済みで Cookie を出し直せない。サーバー側だけ延ばすと Cookie が先に切れる
        val cookieWritable = !call.response.isCommitted
        val session = sessionService.authenticate(token = token, extendable = cookieWritable) ?: return null
        if (session.extended) {
            userCookie.append(token = token, maxAgeSeconds = sessionService.ttlSeconds)
        }
        return session.accountId
    }

    /**
     * 投稿や人ごとに解決するフィールドから何度も読まれるので、1 回の実行の中では最初に確かめた結果を使う。
     * 購読を打ち切るかの判断など、その都度セッションを確かめたいところは [userAccountId] を使う
     */
    val viewerAccountId: AccountId? by lazy { userAccountId() }

    fun issueUserSession(accountId: AccountId) {
        userCookie.append(token = sessionService.create(accountId), maxAgeSeconds = sessionService.ttlSeconds)
    }

    fun revokeUserSession() {
        userCookie.token()?.let(sessionService::delete)
        userCookie.expire()
    }
}
