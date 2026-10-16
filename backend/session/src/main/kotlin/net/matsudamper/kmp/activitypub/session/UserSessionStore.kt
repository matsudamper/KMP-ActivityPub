package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 保存先が漏れても使えないよう、トークンはハッシュで受け取る
 */
interface UserSessionStore {
    fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    )

    /**
     * 期限切れと、消したアカウントのものは返さない
     */
    fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId?

    fun delete(tokenHash: String)
}
