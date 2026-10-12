package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.crypto.PasswordHash
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
     * いるときと同じだけ PBKDF2 を回す
     */
    private val dummyPasswordHash: PasswordHash by lazy { PasswordHash.create("dummy-password") }

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
            dummyPasswordHash.matches(password)
            return null
        }

        return credential.id.takeIf { PasswordHash.parse(credential.passwordHash).matches(password) }
    }
}
