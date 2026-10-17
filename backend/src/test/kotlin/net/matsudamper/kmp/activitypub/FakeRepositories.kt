package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.PasskeySummary
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.StoredPasskey
import net.matsudamper.kmp.activitypub.session.UserSessionStore
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle

internal class FakeRepositories : Repositories {
    override val accountRepository: FakeAccountRepository = FakeAccountRepository()

    override val sessionStore: UserSessionStore = FakeUserSessionStore()

    override val passkeyRepository: PasskeyRepository = FakePasskeyRepository()

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
    private val userHandles = mutableMapOf<AccountId, PasskeyUserHandle>()
    private val passkeys = mutableMapOf<PasskeyCredentialId, StoredPasskey>()
    private val names = mutableMapOf<PasskeyCredentialId, String>()

    override fun userHandle(
        accountId: AccountId,
        newUserHandle: () -> PasskeyUserHandle,
    ): PasskeyUserHandle = userHandles.getOrPut(accountId, newUserHandle)

    override fun passkeys(accountId: AccountId): List<PasskeySummary> =
        passkeys
            .filterValues { it.accountId == accountId }
            .keys
            .map { PasskeySummary(credentialId = it, name = names.getValue(it), createdAt = Instant.EPOCH, lastUsedAt = null) }

    override fun add(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
        publicKey: ByteArray,
        signCount: Long,
        name: String,
        createdAt: Instant,
    ): Boolean {
        if (credentialId in passkeys) return false
        passkeys[credentialId] = StoredPasskey(accountId = accountId, userHandle = userHandles[accountId], publicKey = publicKey, signCount = signCount)
        names[credentialId] = name
        return true
    }

    override fun delete(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
    ): Boolean {
        if (passkeys[credentialId]?.accountId != accountId) return false
        passkeys.remove(credentialId)
        names.remove(credentialId)
        return true
    }

    override fun findByCredentialId(credentialId: PasskeyCredentialId): StoredPasskey? = passkeys[credentialId]

    override fun updateSignCount(
        credentialId: PasskeyCredentialId,
        signCount: Long,
        usedAt: Instant,
    ) {
        val passkey = passkeys[credentialId] ?: return
        passkeys[credentialId] = StoredPasskey(passkey.accountId, passkey.userHandle, passkey.publicKey, maxOf(passkey.signCount, signCount))
    }
}
