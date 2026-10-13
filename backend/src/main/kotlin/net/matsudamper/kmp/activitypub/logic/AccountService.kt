package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class AccountService(
    private val accounts: AccountRepository,
) {
    /**
     * ユーザーがいないときの照合に使う。
     *
     * いないときにすぐ返すと、応答の速さでユーザー名が存在するかが分かる。
     * いるときと同じだけハッシュの計算をする。
     * 遅延生成すると、最初の照合だけ生成の分遅くなりユーザー名の有無が分かるので、作る時点で用意する
     */
    private val dummyPasswordHash: String = PasswordHashType.Current.encoder.encode("dummy-password")!!

    fun account(id: AccountId): Account? = accounts.findById(id)

    /**
     * @return 名前とパスワードが合えばそのユーザー。合わなければ null
     */
    fun authenticate(
        username: String,
        password: String,
    ): AccountId? {
        val credential = accounts.findCredential(username.trim())
        if (credential == null) {
            PasswordHashType.Current.encoder.matches(password, dummyPasswordHash)
            return null
        }

        // 知らない方式は DB の入れ間違い。パスワード違いと区別できるよう失敗にする
        val hashType =
            checkNotNull(PasswordHashType.fromDbValue(credential.passwordHashType)) {
                "対応していない password_hash_type: ${credential.passwordHashType}"
            }
        return credential.id.takeIf { hashType.encoder.matches(password, credential.passwordHash) }
    }
}
