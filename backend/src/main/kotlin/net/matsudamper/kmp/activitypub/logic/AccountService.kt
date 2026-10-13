package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class AccountService(
    private val accounts: AccountRepository,
) {
    /**
     * ユーザーがいなくても同じだけ計算し、応答時間で名前の有無を分からなくする。
     * 遅延生成すると最初の 1 回だけ遅くなる
     */
    private val dummyPasswordHash: String = PasswordHashType.Current.encoder.encode("dummy-password")!!

    fun account(id: AccountId): Account? = accounts.findById(id)

    fun authenticate(
        username: String,
        password: String,
    ): AccountId? {
        val credential = accounts.findCredential(username.trim())
        if (credential == null) {
            PasswordHashType.Current.encoder.matches(password, dummyPasswordHash)
            return null
        }

        // パスワード違いと区別できるよう失敗にする
        val hashType =
            checkNotNull(PasswordHashType.fromDbValue(credential.passwordHashType)) {
                "対応していない password_hash_type: ${credential.passwordHashType}"
            }
        return credential.id.takeIf { hashType.encoder.matches(password, credential.passwordHash) }
    }
}
