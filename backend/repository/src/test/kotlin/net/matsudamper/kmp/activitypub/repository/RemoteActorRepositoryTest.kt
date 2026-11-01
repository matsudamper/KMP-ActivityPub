package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class RemoteActorRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-remote-actor-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @Test
    fun findsExactUsernameOnSpecifiedHost() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.followerRepository.recordFollower(actorUri = "https://a.example/users/alice", preferredUsername = "alice", displayName = "Alice A")
            repositories.followerRepository.recordFollower(actorUri = "https://b.example/users/alice", preferredUsername = "alice", displayName = "Alice B")
            assertEquals("Alice B", repositories.remoteActorRepository.findByAcct("alice", "B.EXAMPLE")?.displayName)
            assertEquals(null, repositories.remoteActorRepository.findByAcct("ali", "b.example"))
            assertEquals(null, repositories.remoteActorRepository.findByAcct("alice", "c.example"))
        }
    }

    @BeforeTest
    fun setUp() {
        TestSchema.applyTo(dbPath)
        Accounts.insert(
            databasePath = dbPath,
            username = "user1",
            passwordHash = "hash",
            passwordHashType = "hash-type",
            publicKeyPem = "public-key",
            privateKeyPem = "private-key",
            createdAt = NOW.toString(),
        )
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `ユーザー名か表示名の一部で大文字小文字を区別せずユーザー名順に探せる`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.followerRepository.recordFollower(actorUri = "https://b.example/users/carol", preferredUsername = "carol", displayName = "Ali Carol")
            repositories.followerRepository.recordFollower(actorUri = "https://a.example/users/alice", preferredUsername = "Alice", displayName = null)
            repositories.followerRepository.recordFollower(actorUri = "https://a.example/users/bob", preferredUsername = "bob", displayName = "Bob")
            val beforeUpdate = Instant.now()
            repositories.followerRepository.updateProfile(
                actorUri = "https://b.example/users/carol",
                profile = RemoteActorProfileRecord(
                    preferredUsername = "carol",
                    displayName = "Ali Carol",
                    profileUrl = null,
                    iconUrl = "https://b.example/carol.png",
                ),
            )

            val results = repositories.remoteActorRepository.searchByName(keyword = "ALI", after = null, limit = 10)
            assertEquals(NOW, results.first().updatedAt)
            assertTrue(results.last().updatedAt >= beforeUpdate)
            assertTrue(results.last().updatedAt <= Instant.now())
            assertEquals(
                listOf(
                    RemoteActorSearchRecord(
                        actorUri = "https://a.example/users/alice",
                        preferredUsername = "Alice",
                        displayName = null,
                        iconUrl = null,
                        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
                    ),
                    RemoteActorSearchRecord(
                        actorUri = "https://b.example/users/carol",
                        preferredUsername = "carol",
                        displayName = "Ali Carol",
                        iconUrl = "https://b.example/carol.png",
                        updatedAt = results.last().updatedAt,
                    ),
                ),
                results,
            )
        }
    }

    @Test
    fun `同じユーザー名はアクターURL順に並び指定した位置より後ろを件数まで探せる`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.followerRepository.recordFollower(actorUri = "https://c.example/users/alice", preferredUsername = "alice", displayName = null)
            repositories.followerRepository.recordFollower(actorUri = "https://a.example/users/alice", preferredUsername = "alice", displayName = null)
            repositories.followerRepository.recordFollower(actorUri = "https://b.example/users/alice", preferredUsername = "alice", displayName = null)

            assertEquals(
                listOf("https://b.example/users/alice"),
                repositories.remoteActorRepository
                    .searchByName(
                        keyword = "alice",
                        after = RemoteActorSearchCursor(preferredUsername = "alice", actorUri = "https://a.example/users/alice"),
                        limit = 1,
                    ).map { it.actorUri },
            )
        }
    }

    @Test
    fun `ASCII以外の大文字を含む名前は入力したままの検索語で探せる`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.followerRepository.recordFollower(actorUri = "https://a.example/users/1", preferredUsername = "user1", displayName = "Élodie")

            assertEquals(
                listOf("https://a.example/users/1"),
                repositories.remoteActorRepository.searchByName(keyword = "Élodie", after = null, limit = 10).map { it.actorUri },
            )
        }
    }

    @Test
    fun `ユーザー名の分からないアクターは返さない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.followerRepository.recordFollower(actorUri = "https://a.example/users/1", preferredUsername = null, displayName = "Alice")

            assertEquals(listOf(), repositories.remoteActorRepository.searchByName(keyword = "alice", after = null, limit = 10))
        }
    }

    private fun FollowerRepository.recordFollower(
        actorUri: String,
        preferredUsername: String?,
        displayName: String?,
    ) {
        record(
            username = "user1",
            follower = RemoteActorRecord(
                actorUri = actorUri,
                inbox = "$actorUri/inbox",
                sharedInbox = null,
                publicKeyPem = "remote-public-key",
                profile = RemoteActorProfileRecord(
                    preferredUsername = preferredUsername,
                    displayName = displayName,
                    profileUrl = null,
                    iconUrl = null,
                ),
            ),
            followActivityUri = "$actorUri/follows/1",
            receivedAt = NOW,
            acceptBody = "accept",
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
