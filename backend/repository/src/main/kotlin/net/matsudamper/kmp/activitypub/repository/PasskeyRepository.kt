package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle

interface PasskeyRepository {
    /**
     * まだ無ければ [newUserHandle] で作ったものを保存して返す
     */
    fun userHandle(
        accountId: AccountId,
        newUserHandle: () -> PasskeyUserHandle,
    ): PasskeyUserHandle

    /**
     * 登録した順
     */
    fun passkeys(accountId: AccountId): List<PasskeySummary>

    /**
     * @return 同じ credential ID が既にあれば追加せず false
     */
    fun add(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
        attestedCredentialData: ByteArray,
        signCount: Long,
        name: String,
        createdAt: Instant,
    ): Boolean

    /**
     * パスワードが無いアカウントの最後のパスキーは、ログインできなくなるので消さない
     */
    fun delete(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
    ): PasskeyDeleteResult

    fun findByCredentialId(credentialId: PasskeyCredentialId): StoredPasskey?

    /**
     * 今より小さい [signCount] では戻さない。同じパスキーのログインが並んで、古い値が後から書かれることがある
     */
    fun updateSignCount(
        credentialId: PasskeyCredentialId,
        signCount: Long,
        usedAt: Instant,
    )
}

class StoredPasskey(
    val accountId: AccountId,
    /**
     * 登録時に作っているので通常は null にならない
     */
    val userHandle: PasskeyUserHandle?,
    val attestedCredentialData: ByteArray,
    val signCount: Long,
)

class PasskeySummary(
    val credentialId: PasskeyCredentialId,
    val name: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)

sealed interface PasskeyDeleteResult {
    data object Deleted : PasskeyDeleteResult

    /**
     * [accountId][PasskeyRepository.delete] のパスキーではない
     */
    data object NotFound : PasskeyDeleteResult

    data object LastLoginMethod : PasskeyDeleteResult
}
