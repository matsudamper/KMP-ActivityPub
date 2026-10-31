package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.PasskeyDeleteResult
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.PasskeySummary
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchCursor
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.StoredPasskey
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle

internal class FakeRepositories : Repositories {
    override val followingRepository: FollowingRepository = object : FollowingRepository {
        private val entries = mutableSetOf<Pair<AccountId, String>>()
        override fun contains(accountId: AccountId, actorUri: String): Boolean = (accountId to actorUri) in entries
        override fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean = entries.add(accountId to actorUri)
        override fun remove(accountId: AccountId, actorUri: String): Boolean = entries.remove(accountId to actorUri)
    }
    override val accountRepository: FakeAccountRepository = FakeAccountRepository(hasPasskey = { passkeyRepository.passkeys(it).isNotEmpty() })
    override val followerRepository: FakeFollowerRepository = FakeFollowerRepository()
    override val noteRepository: FakeNoteRepository = FakeNoteRepository()
    override val remoteActorRepository: FakeRemoteActorRepository = FakeRemoteActorRepository()
    override val deliveryQueueRepository: FakeDeliveryQueueRepository = FakeDeliveryQueueRepository(noteRepository)

    override val passkeyRepository: PasskeyRepository = FakePasskeyRepository(hasPassword = { accountRepository.hasPassword(it) })

    override fun close() = Unit
}

internal class FakeAccountRepository(
    private val hasPasskey: (AccountId) -> Boolean,
) : AccountRepository {
    private val rows = mutableListOf<AccountCredential>()
    private val passwordDeletedIds = mutableSetOf<AccountId>()
    private val publicKeyPems = mutableMapOf<AccountId, String>()
    private val iconPaths = mutableMapOf<AccountId, String>()

    fun add(
        username: String,
        passwordHash: String,
        passwordHashType: String,
    ): AccountId {
        val id = AccountId(rows.size + 1L)
        rows += AccountCredential(id = id, username = username, passwordHash = passwordHash, passwordHashType = passwordHashType)
        return id
    }

    fun addPublicKeyPem(
        id: AccountId,
        publicKeyPem: String,
    ) {
        publicKeyPems[id] = publicKeyPem
    }

    fun setIconPath(
        id: AccountId,
        iconPath: String,
    ) {
        iconPaths[id] = iconPath
    }

    override fun findById(id: AccountId): Account? =
        rows.firstOrNull { it.id == id }?.toAccount()

    override fun findByUsername(username: String): Account? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }?.toAccount()

    override fun searchByUsername(
        keyword: String,
        afterUsername: String?,
        limit: Int,
    ): List<Account> =
        rows
            .filter { it.username.contains(keyword, ignoreCase = true) }
            .filter { afterUsername == null || it.username.lowercase() > afterUsername.lowercase() }
            .sortedBy { it.username.lowercase() }
            .take(limit)
            .map { it.toAccount() }

    override fun findCredential(username: String): AccountCredential? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }?.takeIf { it.id !in passwordDeletedIds }

    override fun findPublicKeyPem(username: String): String? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }?.let { publicKeyPems[it.id] }

    override fun findPrivateKeyPem(username: String): String? = null

    override fun hasPassword(id: AccountId): Boolean = rows.any { it.id == id } && id !in passwordDeletedIds

    override fun deletePasswordIfPasskeyExists(id: AccountId): Boolean {
        if (hasPassword(id).not() || hasPasskey(id).not()) return false
        passwordDeletedIds += id
        return true
    }

    private fun AccountCredential.toAccount(): Account =
        Account(id = id, username = username, createdAt = Instant.EPOCH, displayName = null, iconPath = iconPaths[id])
}

private class FakePasskeyRepository(
    private val hasPassword: (AccountId) -> Boolean,
) : PasskeyRepository {
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
        attestedCredentialData: ByteArray,
        signCount: Long,
        name: String,
        createdAt: Instant,
    ): Boolean {
        if (credentialId in passkeys) return false
        passkeys[credentialId] = StoredPasskey(accountId = accountId, userHandle = userHandles[accountId], attestedCredentialData = attestedCredentialData, signCount = signCount)
        names[credentialId] = name
        return true
    }

    override fun delete(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
    ): PasskeyDeleteResult {
        if (passkeys[credentialId]?.accountId != accountId) return PasskeyDeleteResult.NotFound
        if (hasPassword(accountId).not() && passkeys(accountId).size <= 1) return PasskeyDeleteResult.LastLoginMethod
        passkeys.remove(credentialId)
        names.remove(credentialId)
        return PasskeyDeleteResult.Deleted
    }

    override fun findByCredentialId(credentialId: PasskeyCredentialId): StoredPasskey? = passkeys[credentialId]

    override fun updateSignCount(
        credentialId: PasskeyCredentialId,
        signCount: Long,
        usedAt: Instant,
    ) {
        val passkey = passkeys[credentialId] ?: return
        passkeys[credentialId] = StoredPasskey(passkey.accountId, passkey.userHandle, passkey.attestedCredentialData, maxOf(passkey.signCount, signCount))
    }
}

internal class FakeRemoteActorRepository : RemoteActorRepository {
    private val rows = mutableListOf<RemoteActorSearchRecord>()

    fun add(record: RemoteActorSearchRecord) {
        rows += record
    }

    override fun searchByName(
        keyword: String,
        after: RemoteActorSearchCursor?,
        limit: Int,
    ): List<RemoteActorSearchRecord> =
        rows
            .filter { it.preferredUsername.contains(keyword, ignoreCase = true) || it.displayName?.contains(keyword, ignoreCase = true) == true }
            .sortedWith(compareBy<RemoteActorSearchRecord> { it.preferredUsername.sqliteLower() }.thenBy { it.actorUri })
            .filter { record ->
                after == null ||
                    compareValuesBy(
                        record,
                        RemoteActorSearchRecord(actorUri = after.actorUri, preferredUsername = after.preferredUsername, displayName = null, iconUrl = null),
                        { it.preferredUsername.sqliteLower() },
                        { it.actorUri },
                    ) > 0
            }.take(limit)
}

/**
 * SQLite の lower() と同じく ASCII だけを小文字にする。並び順を本物の SQLite に揃える
 */
private fun String.sqliteLower(): String = map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")
