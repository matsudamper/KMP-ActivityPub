package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun listIsNewestFirstPagedAndFilledWithReceivedProfiles() {
        val directory = createTempDirectory("following-list-test")
        val path = directory.resolve("test.db")
        try {
            TestSchema.applyTo(path)
            Accounts.insert(
                databasePath = path,
                username = "alice",
                passwordHash = "hash",
                passwordHashType = "hash-type",
                publicKeyPem = "public-key",
                privateKeyPem = "private-key",
                createdAt = Instant.EPOCH.toString(),
            )
            val knownActorUri = "https://remote.example/users/carol"
            val unknownActorUri = "https://remote.example/users/dave"
            val sameTimeActorUri = "https://remote.example/users/erin"
            Repositories.create(DatabaseConfig(path = path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                repositories.followerRepository.record(
                    username = "alice",
                    follower = RemoteActorRecord(
                        actorUri = knownActorUri,
                        inbox = "$knownActorUri/inbox",
                        sharedInbox = null,
                        publicKeyPem = "remote-public-key",
                        profile = RemoteActorProfileRecord(
                            preferredUsername = "carol",
                            displayName = "Carol",
                            profileUrl = null,
                            iconUrl = "https://remote.example/carol.png",
                        ),
                    ),
                    followActivityUri = "$knownActorUri/follows/1",
                    receivedAt = Instant.EPOCH,
                    acceptBody = "accept",
                )
                val following = repositories.followingRepository
                following.add(alice, knownActorUri, Instant.EPOCH)
                following.add(alice, unknownActorUri, Instant.EPOCH.plusSeconds(1))
                following.add(alice, sameTimeActorUri, Instant.EPOCH.plusSeconds(1))

                val firstPage = following.list(alice, after = null, limit = 2)
                assertEquals(listOf(sameTimeActorUri, unknownActorUri), firstPage.map { it.actorUri })
                assertNull(firstPage.last().preferredUsername)

                val last = firstPage.last()
                val secondPage = following.list(alice, after = FollowingPosition(createdAt = last.createdAt, actorUri = last.actorUri), limit = 2)
                assertEquals(listOf(knownActorUri), secondPage.map { it.actorUri })
                assertEquals("carol", secondPage.single().preferredUsername)
                assertEquals("Carol", secondPage.single().displayName)
                assertEquals("https://remote.example/carol.png", secondPage.single().iconUrl)
            }
        } finally {
            directory.toFile().listFiles().orEmpty().forEach { it.toPath().deleteIfExists() }
            directory.deleteIfExists()
        }
    }
}
