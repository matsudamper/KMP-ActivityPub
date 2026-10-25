package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import net.matsudamper.kmp.activitypub.crypto.AccountKeyPair
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.actor.ActorUsernameUtil

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

    fun publicKeyPem(username: String): String? = accountRepository.findPublicKeyPem(username)

    fun privateKeyPem(username: String): String? = accountRepository.findPrivateKeyPem(username)

    fun hasPassword(id: AccountId): Boolean = accountRepository.hasPassword(id)

    fun deletePassword(id: AccountId): DeletePasswordResult =
        when {
            accountRepository.deletePasswordIfPasskeyExists(id) -> DeletePasswordResult.Success
            accountRepository.hasPassword(id) -> DeletePasswordResult.NoPasskey
            else -> DeletePasswordResult.Success
        }

    /**
     * 名前順で [afterUsername] の次から [limit] 件返す
     */
    fun accounts(
        afterUsername: String?,
        limit: Int,
    ): AccountsPage {
        if (limit <= 0) return AccountsPage(accounts = listOf(), nextCursor = null)

        val fetched = accountRepository.searchByUsername(keyword = "", afterUsername = afterUsername, limit = limit + 1)
        val page = fetched.take(limit)
        return AccountsPage(
            accounts = page,
            nextCursor = page.lastOrNull()?.username?.takeIf { fetched.size > limit },
        )
    }

    fun add(
        username: String,
        password: String,
    ): AddAccountResult {
        val trimmed = username.trim()

        val unusableCharacters = ActorUsernameUtil.unusableCharacters(trimmed)
        val tooShort = trimmed.length < ActorUsernameUtil.MIN_LENGTH
        val tooLong = trimmed.length > ActorUsernameUtil.MAX_LENGTH
        val passwordTooShort = password.length < PASSWORD_MIN_LENGTH

        // 入力として通らないうちは重複を見に行かない。DB を引いても結果が変わらない
        if (unusableCharacters.isNotEmpty() || tooShort || tooLong || passwordTooShort) {
            return AddAccountResult.Failure(
                unusableCharacters = unusableCharacters,
                tooShort = tooShort,
                tooLong = tooLong,
                duplicated = false,
                passwordTooShort = passwordTooShort,
            )
        }

        val keyPair = AccountKeyPair.generate()
        val added = accountRepository.add(
            username = trimmed,
            passwordHash = checkNotNull(PasswordHashType.Current.encoder.encode(password)),
            passwordHashType = PasswordHashType.Current.dbValue,
            publicKeyPem = keyPair.publicKeyPem,
            privateKeyPem = keyPair.privateKeyPem,
            createdAt = Instant.now(),
        ) ?: return AddAccountResult.Failure(
            unusableCharacters = listOf(),
            tooShort = false,
            tooLong = false,
            duplicated = true,
            passwordTooShort = false,
        )

        return AddAccountResult.Success(added)
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

    /**
     * @param nextCursor 続きがある場合の、次に渡す名前
     */
    data class AccountsPage(
        val accounts: List<Account>,
        val nextCursor: String?,
    )

    sealed interface AddAccountResult {
        data class Success(
            val account: Account,
        ) : AddAccountResult

        /**
         * 通らなかった理由。1 回の入力で複数当てはまることがあるので並べて返す。
         *
         * @param unusableCharacters 名前に含まれていた使えない文字
         * @param duplicated 同じ名前のユーザーが既にいる
         */
        data class Failure(
            val unusableCharacters: List<Char>,
            val tooShort: Boolean,
            val tooLong: Boolean,
            val duplicated: Boolean,
            val passwordTooShort: Boolean,
        ) : AddAccountResult
    }

    companion object {
        const val PASSWORD_MIN_LENGTH: Int = 8
    }
}
