package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class AccountService(
    private val accountRepository: AccountRepository,
) {
    /**
     * ユーザーがいなくても同じだけ計算し、応答時間で名前の有無を分からなくする。
     * 遅延生成すると最初の 1 回だけ遅くなるので最初から初期化しておく
     */
    private val dummyPasswordHash: String = checkNotNull(PasswordHashType.Current.encoder.encode("dummy-password"))

    fun account(id: AccountId): Account? = accountRepository.findById(id)

    fun account(username: String): Account? = accountRepository.findByUsername(username)

    fun searchAccounts(
        keyword: String,
        cursor: AccountSearchCursor?,
    ): AccountSearchResult {
        val trimmedKeyword = keyword.trim()
        if (trimmedKeyword.isEmpty()) return AccountSearchResult(accounts = listOf(), nextCursor = null)

        // 1 件多く取り、続きの有無を判定する
        val fetchedAccounts =
            accountRepository.searchByUsername(
                keyword = trimmedKeyword,
                afterUsername = cursor?.lastUsername,
                limit = SEARCH_PAGE_SIZE + 1,
            )
        val accounts = fetchedAccounts.take(SEARCH_PAGE_SIZE)
        return AccountSearchResult(
            accounts = accounts,
            nextCursor =
            if (fetchedAccounts.size > SEARCH_PAGE_SIZE) {
                AccountSearchCursor(lastUsername = accounts.last().username)
            } else {
                null
            },
        )
    }

    fun publicKeyPem(username: String): String? = accountRepository.findPublicKeyPem(username)

    fun privateKeyPem(username: String): String? = accountRepository.findPrivateKeyPem(username)

    fun hasPassword(id: AccountId): Boolean = accountRepository.hasPassword(id)

    fun deletePassword(id: AccountId): DeletePasswordResult =
        when {
            accountRepository.deletePasswordIfPasskeyExists(id) -> DeletePasswordResult.Success
            accountRepository.hasPassword(id) -> DeletePasswordResult.NoPasskey
            else -> DeletePasswordResult.Success
        }

    fun authenticate(
        username: String,
        password: String,
    ): AccountId? {
        val credential = accountRepository.findCredential(username.trim())
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

    sealed interface DeletePasswordResult {
        data object Success : DeletePasswordResult

        data object NoPasskey : DeletePasswordResult
    }
}

data class AccountSearchCursor(
    val lastUsername: String,
)

data class AccountSearchResult(
    val accounts: List<Account>,
    val nextCursor: AccountSearchCursor?,
)

private const val SEARCH_PAGE_SIZE = 30
