package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import net.matsudamper.activitypub.actor.ActorUrls
import net.matsudamper.activitypub.actor.ActorUsernameUtil
import net.matsudamper.kmp.activitypub.crypto.PasswordHash
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountPosition
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ユーザー（= こちらのアクター）の登録と照合。
 */
class AccountService(
    private val accounts: AccountRepository,
    private val domain: String,
) {
    /**
     * ユーザーがいないときの照合に使う。
     *
     * いないときにすぐ返すと、応答の速さでユーザー名が存在するかが分かる。
     * いるときと同じだけ PBKDF2 を回す
     */
    private val dummyPasswordHash: PasswordHash by lazy { PasswordHash.create("dummy-password") }

    fun account(id: AccountId): ManagedAccount? = accounts.findById(id)?.toManaged()

    /**
     * 登録した順で [after] の次から [limit] 件返す
     */
    fun accounts(after: AccountPosition?, limit: Int): ManagedAccountsPage {
        if (limit <= 0) {
            return ManagedAccountsPage(accounts = listOf(), hasMore = false, nextPosition = null)
        }

        val fetched = accounts.list(after = after, limit = limit + 1)
        val hasMore = fetched.size > limit
        val page = fetched.take(limit)

        return ManagedAccountsPage(
            accounts = page.map { it.toManaged() },
            hasMore = hasMore,
            nextPosition = if (hasMore) page.last().position() else null,
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

        val added = accounts.add(
            username = trimmed,
            passwordHash = PasswordHash.create(password).encode(),
            createdAt = Instant.now(),
        ) ?: return AddAccountResult.Failure(
            unusableCharacters = listOf(),
            tooShort = false,
            tooLong = false,
            duplicated = true,
            passwordTooShort = false,
        )

        return AddAccountResult.Success(added.toManaged())
    }

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

    private fun Account.toManaged(): ManagedAccount = ManagedAccount(
        urls = ActorUrls(domain = domain, username = username),
        accountId = id,
        createdAt = createdAt,
    )

    data class ManagedAccount(
        val urls: ActorUrls,
        val accountId: AccountId,
        val createdAt: Instant,
    )

    /**
     * @param nextPosition 続きがある場合の、次に渡す `after`
     */
    data class ManagedAccountsPage(
        val accounts: List<ManagedAccount>,
        val hasMore: Boolean,
        val nextPosition: AccountPosition?,
    )

    sealed interface AddAccountResult {
        data class Success(
            val account: ManagedAccount,
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
