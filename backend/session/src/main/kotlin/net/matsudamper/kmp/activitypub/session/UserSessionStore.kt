package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * Redis などへは実装を差し替える。サーバーの再起動で消えない保存先にすること
 *
 * 保存先が漏れても使えないよう、トークンはハッシュで受け取る
 */
interface UserSessionStore {
    fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    )

    fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId?
}
