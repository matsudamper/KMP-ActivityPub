package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * credential ID と user handle は base64url（パディング無し）で受け渡す
 */
interface PasskeyRepository {
    /**
     * まだ無ければ [newUserHandle] で作ったものを保存して返す
     */
    fun userHandle(
        accountId: AccountId,
        newUserHandle: () -> String,
    ): String

    /**
     * 登録した順
     */
    fun passkeys(accountId: AccountId): List<PasskeySummary>

    /**
     * @return 同じ credential ID が既にあれば追加せず false
     */
    fun add(
        accountId: AccountId,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
        name: String,
        createdAt: Instant,
    ): Boolean

    /**
     * @return [accountId] のパスキーが無ければ false
     */
    fun delete(
        accountId: AccountId,
        credentialId: String,
    ): Boolean

    fun findByCredentialId(credentialId: String): StoredPasskey?

    fun updateSignCount(
        credentialId: String,
        signCount: Long,
        usedAt: Instant,
    )
}

class StoredPasskey(
    val accountId: AccountId,
    /**
     * 登録時に作っているので通常は null にならない
     */
    val userHandle: String?,
    val publicKey: ByteArray,
    val signCount: Long,
)

class PasskeySummary(
    val credentialId: String,
    val name: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)
