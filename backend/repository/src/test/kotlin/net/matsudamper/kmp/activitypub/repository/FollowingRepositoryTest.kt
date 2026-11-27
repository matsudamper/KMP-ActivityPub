package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId

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
    fun groupIdsAmongReturnsOnlyFollowedActorsWithTheirGroup() {
        val directory = createTempDirectory("following-groups-test")
        val path = directory.resolve("test.db")
        try {
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "hash-type", "public-key", "private-key", Instant.EPOCH.toString())
            }
            Repositories.create(DatabaseConfig(path = path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                val following = repositories.followingRepository
                val inMain = "https://remote.example/users/main"
                val inGroup = "https://remote.example/users/group"
                val notFollowed = "https://remote.example/users/none"
                val news = repositories.followingGroupRepository.create(alice, "ニュース", Instant.EPOCH)
                following.add(alice, inMain, Instant.EPOCH)
                following.add(alice, inGroup, Instant.EPOCH)
                following.setGroup(alice, inGroup, news.id)
                following.add(bob, notFollowed, Instant.EPOCH)

                assertEquals(mapOf(inMain to null, inGroup to news.id), following.groupIdsAmong(alice, setOf(inMain, inGroup, notFollowed)))
                assertEquals(mapOf(), following.groupIdsAmong(alice, setOf()))
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
                            headerUrl = null,
                            emojis = listOf(),
                            summary = null,
                            fields = listOf(),
                        ),
                    ),
                    followerHost = null,
                    followActivityUri = "$knownActorUri/follows/1",
                    receivedAt = Instant.EPOCH,
                    acceptBody = "accept",
                )
                val following = repositories.followingRepository
                following.add(alice, knownActorUri, Instant.EPOCH)
                following.add(alice, unknownActorUri, Instant.EPOCH.plusSeconds(1))
                following.add(alice, sameTimeActorUri, Instant.EPOCH.plusSeconds(1))
                assertEquals(3, following.count(alice))

                val firstPage = following.list(alice, after = null, limit = 2)
                assertEquals(listOf(sameTimeActorUri, unknownActorUri), firstPage.map { it.actorUri })
                assertNull(firstPage.last().preferredUsername)

                val last = firstPage.last()
                val secondPage = following.list(alice, after = ActorListPosition(listedAt = last.listedAt, actorUri = last.actorUri), limit = 2)
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

    @Test
    fun acceptedActorUrisListOnlyAcceptedFollowingInUrlOrder() {
        val directory = createTempDirectory("following-accepted-test")
        val path = directory.resolve("test.db")
        try {
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "hash-type", "public-key", "private-key", Instant.EPOCH.toString())
            }
            Repositories.create(DatabaseConfig(path = path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                val following = repositories.followingRepository
                val acceptedB = "https://remote.example/users/b"
                val acceptedA = "https://remote.example/users/a"
                val pending = "https://remote.example/users/pending"
                val bobOnly = "https://remote.example/users/bob-only"
                fun follow(accountId: AccountId, actorUri: String, accepted: Boolean) {
                    following.add(accountId, actorUri, Instant.EPOCH)
                    following.recordActivity(accountId, actorUri, "$actorUri#follow-$accountId")
                    if (accepted) following.respondToFollow(actorUri, "$actorUri#follow-$accountId", Instant.EPOCH)
                }
                follow(alice, acceptedB, accepted = true)
                follow(alice, acceptedA, accepted = true)
                follow(alice, pending, accepted = false)
                follow(bob, bobOnly, accepted = true)

                assertEquals(listOf(acceptedA, acceptedB), following.listAcceptedActorUris("alice", after = null, limit = 10))
                assertEquals(listOf(acceptedB), following.listAcceptedActorUris("alice", after = acceptedA, limit = 10))
                assertEquals(listOf(acceptedA), following.listAcceptedActorUris("ALICE", after = null, limit = 1))
                assertEquals(2L, following.countAccepted("alice"))
                assertEquals(0L, following.countAccepted("nobody"))
            }
        } finally {
            directory.toFile().listFiles().orEmpty().forEach { it.toPath().deleteIfExists() }
            directory.deleteIfExists()
        }
    }

    @Test
    fun accountIdsFollowingReturnsOnlyAccountsFollowingTheActor() {
        val directory = createTempDirectory("following-accounts-test")
        val path = directory.resolve("test.db")
        try {
            TestSchema.applyTo(path)
            listOf("alice", "bob", "carol").forEach { username ->
                Accounts.insert(path, username, "hash", "hash-type", "public-key", "private-key", Instant.EPOCH.toString())
            }
            Repositories.create(DatabaseConfig(path = path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                val carol = checkNotNull(repositories.accountRepository.findByUsername("carol")).id
                val actorUri = "https://remote.example/users/dave"
                val following = repositories.followingRepository
                following.add(alice, actorUri, Instant.EPOCH)
                following.add(bob, actorUri, Instant.EPOCH)
                following.add(carol, "https://remote.example/users/erin", Instant.EPOCH)

                assertEquals(listOf(alice, bob), following.accountIdsFollowing(actorUri))
                assertEquals(listOf(), following.accountIdsFollowing("https://remote.example/users/frank"))
            }
        } finally {
            directory.toFile().listFiles().orEmpty().forEach { it.toPath().deleteIfExists() }
            directory.deleteIfExists()
        }
    }
}
