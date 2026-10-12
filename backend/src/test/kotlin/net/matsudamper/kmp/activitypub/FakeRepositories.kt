package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.UserSessionRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ルーティングのテストで本物の DB を用意しないための置き換え
 */
internal class FakeRepositories : Repositories {
    override val accounts: FakeAccountRepository = FakeAccountRepository()

    override val userSessions: UserSessionRepository = FakeUserSessionRepository()

    override fun verifyWritable() = Unit

    override fun close() = Unit
}

internal class FakeAccountRepository : AccountRepository {
    private val rows = mutableListOf<AccountCredential>()

    /**
     * 本番では sqlite3 で直接入れるので、テストだけが持つ口
     */
    fun add(
        username: String,
        passwordHash: String,
    ): AccountId {
        val id = AccountId(rows.size + 1L)
        rows += AccountCredential(id = id, username = username, passwordHash = passwordHash)
        return id
    }

    override fun findById(id: AccountId): Account? =
        rows.firstOrNull { it.id == id }?.let { Account(id = it.id, username = it.username, createdAt = Instant.EPOCH) }

    override fun findCredential(username: String): AccountCredential? = rows.firstOrNull { it.username.equals(username, ignoreCase = true) }
}

private class FakeUserSessionRepository : UserSessionRepository {
    private val rows = mutableMapOf<String, Pair<AccountId, Instant>>()

    override fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        rows[tokenHash] = accountId to expiresAt
    }

    override fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId? = rows[tokenHash]?.takeIf { (_, expiresAt) -> expiresAt > now }?.first
}
