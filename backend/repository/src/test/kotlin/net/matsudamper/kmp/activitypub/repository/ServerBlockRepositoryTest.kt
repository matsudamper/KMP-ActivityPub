package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kmp.activitypub.shared.AccountId

class ServerBlockRepositoryTest {
    @Test
    fun hostsAreListedNewestFirstPerAccountWithoutDuplicates() {
        withAliceAndBob { repositories, alice, bob ->
            val blocks = repositories.serverBlockRepository
            blocks.add(alice, "old.example", Instant.ofEpochSecond(1))
            blocks.add(alice, "new.example", Instant.ofEpochSecond(2))
            blocks.add(alice, "old.example", Instant.ofEpochSecond(3))
            blocks.add(bob, "bob.example", Instant.ofEpochSecond(1))
            assertEquals(listOf("new.example", "old.example"), blocks.hosts(alice))
            assertEquals(listOf("bob.example"), blocks.hosts(bob))
        }
    }

    @Test
    fun removeDeletesOnlyThatAccountsHost() {
        withAliceAndBob { repositories, alice, bob ->
            val blocks = repositories.serverBlockRepository
            blocks.add(alice, "remote.example", Instant.EPOCH)
            blocks.add(bob, "remote.example", Instant.EPOCH)
            blocks.remove(alice, "remote.example")
            blocks.remove(alice, "missing.example")
            assertEquals(listOf(), blocks.hosts(alice))
            assertEquals(listOf("remote.example"), blocks.hosts(bob))
        }
    }

    private fun withAliceAndBob(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("server-block-test")
        val path = directory.resolve("test.db")
        try {
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(
                    databasePath = path,
                    username = username,
                    passwordHash = "hash",
                    passwordHashType = "hash-type",
                    publicKeyPem = "public-key",
                    privateKeyPem = "private-key",
                    createdAt = Instant.EPOCH.toString(),
                )
            }
            Repositories.create(DatabaseConfig(path = path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                block(repositories, alice, bob)
            }
        } finally {
            directory.toFile().listFiles().orEmpty().forEach { it.toPath().deleteIfExists() }
            directory.deleteIfExists()
        }
    }
}
