package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 保存先が漏れても使えないよう、トークンはハッシュで受け取る
 */
interface UserSessionStore : AutoCloseable {
    fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    )

    fun find(
        tokenHash: String,
        now: Instant,
    ): StoredUserSession?

    /**
     * @return 期限が切れて消えていたら false
     */
    fun extend(
        tokenHash: String,
        expiresAt: Instant,
    ): Boolean

    fun delete(tokenHash: String)
}

data class StoredUserSession(
    val accountId: AccountId,
    val expiresAt: Instant,
)
