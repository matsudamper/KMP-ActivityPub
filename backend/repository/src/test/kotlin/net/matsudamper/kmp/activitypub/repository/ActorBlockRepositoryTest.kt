package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kmp.activitypub.shared.AccountId

class ActorBlockRepositoryTest {
    @Test
    fun actorsAreListedNewestFirstPerAccountAndReblockUpdatesAcct() {
        withAliceAndBob { repositories, alice, bob ->
            val blocks = repositories.actorBlockRepository
            blocks.add(alice, BlockedActor("https://old.example/users/a", "a@old.example"), Instant.ofEpochSecond(1))
            blocks.add(alice, BlockedActor("https://new.example/users/b", "b@new.example"), Instant.ofEpochSecond(2))
            blocks.add(alice, BlockedActor("https://old.example/users/a", "renamed@old.example"), Instant.ofEpochSecond(3))
            blocks.add(bob, BlockedActor("https://bob.example/users/c", "c@bob.example"), Instant.ofEpochSecond(1))
            assertEquals(
                listOf(
                    BlockedActor("https://new.example/users/b", "b@new.example"),
                    BlockedActor("https://old.example/users/a", "renamed@old.example"),
                ),
                blocks.list(alice),
            )
            assertEquals(listOf(BlockedActor("https://bob.example/users/c", "c@bob.example")), blocks.list(bob))
        }
    }

    @Test
    fun removeDeletesOnlyThatAccountsActor() {
        withAliceAndBob { repositories, alice, bob ->
            val blocks = repositories.actorBlockRepository
            val actor = BlockedActor("https://remote.example/users/a", "a@remote.example")
            blocks.add(alice, actor, Instant.EPOCH)
            blocks.add(bob, actor, Instant.EPOCH)
            blocks.remove(alice, actor.actorUri)
            blocks.remove(alice, "https://missing.example/users/x")
            assertEquals(listOf(), blocks.list(alice))
            assertEquals(listOf(actor), blocks.list(bob))
        }
    }

    private fun withAliceAndBob(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("actor-block-test")
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
