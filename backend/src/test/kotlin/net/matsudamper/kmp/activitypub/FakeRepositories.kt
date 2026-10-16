package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.PasskeyChallengePurpose
import net.matsudamper.kmp.activitypub.repository.PasskeyChallengeStore
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.StoredPasskey
import net.matsudamper.kmp.activitypub.session.UserSessionStore
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class FakeRepositories : Repositories {
    override val accountRepository: FakeAccountRepository = FakeAccountRepository()

    override val sessionStore: UserSessionStore = FakeUserSessionStore()

    override val passkeyRepository: PasskeyRepository = FakePasskeyRepository()

    override val passkeyChallengeStore: PasskeyChallengeStore = FakePasskeyChallengeStore()

    override fun close() = Unit
}

internal class FakeAccountRepository : AccountRepository {
    private val rows = mutableListOf<AccountCredential>()

    fun add(
        username: String,
        passwordHash: String,
        passwordHashType: String,
    ): AccountId {
        val id = AccountId(rows.size + 1L)
        rows += AccountCredential(id = id, username = username, passwordHash = passwordHash, passwordHashType = passwordHashType)
        return id
    }

    override fun findById(id: AccountId): Account? =
        rows.firstOrNull { it.id == id }?.let { Account(id = it.id, username = it.username, createdAt = Instant.EPOCH) }

    override fun findCredential(username: String): AccountCredential? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }
}

private class FakeUserSessionStore : UserSessionStore {
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

private class FakePasskeyRepository : PasskeyRepository {
    private val userHandles = mutableMapOf<AccountId, String>()
    private val passkeys = mutableMapOf<String, StoredPasskey>()

    override fun userHandle(
        accountId: AccountId,
        newUserHandle: () -> String,
    ): String = userHandles.getOrPut(accountId, newUserHandle)

    override fun credentialIds(accountId: AccountId): List<String> = passkeys.filterValues { it.accountId == accountId }.keys.toList()

    override fun add(
        accountId: AccountId,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
        createdAt: Instant,
    ): Boolean {
        if (credentialId in passkeys) return false
        passkeys[credentialId] = StoredPasskey(accountId = accountId, userHandle = userHandles[accountId], publicKey = publicKey, signCount = signCount)
        return true
    }

    override fun findByCredentialId(credentialId: String): StoredPasskey? = passkeys[credentialId]

    override fun updateSignCount(
        credentialId: String,
        signCount: Long,
        usedAt: Instant,
    ) {
        val passkey = passkeys[credentialId] ?: return
        passkeys[credentialId] = StoredPasskey(passkey.accountId, passkey.userHandle, passkey.publicKey, signCount)
    }
}

private class FakePasskeyChallengeStore : PasskeyChallengeStore {
    private val rows = mutableMapOf<String, Triple<PasskeyChallengePurpose, AccountId?, Instant>>()

    override fun create(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        rows[challenge] = Triple(purpose, accountId, expiresAt)
    }

    override fun consume(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        now: Instant,
    ): Boolean {
        val row = rows[challenge] ?: return false
        if (row != Triple(purpose, accountId, row.third) || row.third <= now) return false
        rows.remove(challenge)
        return true
    }
}
