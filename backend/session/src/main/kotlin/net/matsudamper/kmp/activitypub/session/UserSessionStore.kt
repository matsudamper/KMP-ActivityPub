package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * セッションの保存先。実装を差し替えれば、DB 以外（Redis など）にも置ける。
 *
 * サーバーの再起動で消えない保存先にすること。Android アプリは開くたびにログインし直させられない。
 *
 * トークンそのものは受け取らない。呼び出し側がハッシュにしてから渡す。
 * 保存先が漏れても、中身をそのままセッションとして使えないようにするため
 */
interface UserSessionStore {
    fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    )

    /**
     * 期限内のセッションの持ち主を返す。期限切れのものは返さない
     */
    fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId?
}
