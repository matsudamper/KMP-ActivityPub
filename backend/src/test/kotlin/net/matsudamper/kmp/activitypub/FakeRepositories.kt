package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class FakeRepositories : Repositories {
    override val accountRepository: FakeAccountRepository = FakeAccountRepository()

    override fun close() = Unit
}

internal class FakeAccountRepository : AccountRepository {
    private val rows = mutableListOf<AccountCredential>()
    private val privateKeyPems = mutableMapOf<AccountId, String>()

    fun add(
        username: String,
        passwordHash: String,
        passwordHashType: String,
    ): AccountId {
        val id = AccountId(rows.size + 1L)
        rows += AccountCredential(id = id, username = username, passwordHash = passwordHash, passwordHashType = passwordHashType)
        return id
    }

    fun addPrivateKeyPem(
        id: AccountId,
        privateKeyPem: String,
    ) {
        privateKeyPems[id] = privateKeyPem
    }

    override fun findById(id: AccountId): Account? =
        rows.firstOrNull { it.id == id }?.let { Account(id = it.id, username = it.username, createdAt = Instant.EPOCH) }

    override fun findByUsername(username: String): Account? =
        findCredential(username)?.let { Account(id = it.id, username = it.username, createdAt = Instant.EPOCH) }

    override fun findCredential(username: String): AccountCredential? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }

    override fun findPrivateKeyPem(id: AccountId): String? = privateKeyPems[id]
}
