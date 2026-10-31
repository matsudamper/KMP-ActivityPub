package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowingRepositoryTest {
    @Test
    fun followingIsPersistentIdempotentAndIsolatedByAccount() {
        val directory = createTempDirectory("following-test")
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
            val config = DatabaseConfig(path = path)
            val actorUri = "https://remote.example/users/carol"
            Repositories.create(config).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                val following = repositories.followingRepository
                assertFalse(following.contains(alice, actorUri))
                assertTrue(following.add(alice, actorUri, Instant.EPOCH))
                assertFalse(following.add(alice, actorUri, Instant.EPOCH))
                assertTrue(following.contains(alice, actorUri))
                assertFalse(following.contains(bob, actorUri))
                assertFalse(following.remove(bob, actorUri))
            }
            Repositories.create(config).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val following = repositories.followingRepository
                assertTrue(following.contains(alice, actorUri))
                assertTrue(following.remove(alice, actorUri))
                assertFalse(following.remove(alice, actorUri))
                assertFalse(following.contains(alice, actorUri))
                assertTrue(following.add(alice, actorUri, Instant.EPOCH))
            }
        } finally {
            directory.toFile().listFiles().orEmpty().forEach { it.toPath().deleteIfExists() }
            directory.deleteIfExists()
        }
    }
}
