package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ログイン中のユーザーのセッション。
 *
 * トークンそのものは受け取らない。呼び出し側がハッシュにしてから渡す。
 * DB が漏れても、中身をそのままセッションとして使えないようにするため
 */
interface UserSessionRepository {
    fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    )

    /**
     * 期限内のセッションの持ち主を返す。期限切れと、消したアカウントのものは返さない
     */
    fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId?

    fun delete(tokenHash: String)
}
