package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class FollowerRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-follower-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `Accept を送るまではフォロワーに出さない`() {
        withRepository { repository ->
            record(repository, body = "accept-1")

            assertEquals(listOf(), repository.listAccepted(username = "user1", after = null, limit = 10))
            assertEquals(0, repository.countAccepted("user1"))
            assertEquals(listOf(), repository.deliveryTargets("user1"))
        }
    }

    @Test
    fun `Accept を送るとフォロワーに出る`() {
        withRepository { repository ->
            record(repository, body = "accept-1")

            val pending = repository.findDueAccepts(now = RECEIVED_AT, limit = 10).single()
            assertEquals(
                PendingFollowAccept(
                    followeeUsername = "user1",
                    followerActorUri = ALICE.actorUri,
                    followerInbox = ALICE.inbox,
                    body = "accept-1",
                    attempts = 0,
                ),
                pending,
            )
            repository.markAccepted(accept = pending, acceptedAt = RECEIVED_AT)

            assertEquals(listOf(ALICE.actorUri), repository.listAccepted(username = "USER1", after = null, limit = 10))
            assertEquals(1, repository.countAccepted("user1"))
            assertEquals(listOf(ALICE.sharedInbox), repository.deliveryTargets("user1"))
            assertEquals(listOf(), repository.findDueAccepts(now = RECEIVED_AT, limit = 10))
        }
    }

    @Test
    fun `送っている間に置き換えられた Accept は送れたことにしない`() {
        withRepository { repository ->
            record(repository, body = "accept-1")
            val old = repository.findDueAccepts(now = RECEIVED_AT, limit = 10).single()
            record(repository, body = "accept-2")

            repository.markAccepted(accept = old, acceptedAt = RECEIVED_AT)

            assertEquals(0, repository.countAccepted("user1"))
            assertEquals("accept-2", repository.findDueAccepts(now = RECEIVED_AT, limit = 10).single().body)
        }
    }

    @Test
    fun `送り直しは次の時刻まで出さない`() {
        withRepository { repository ->
            record(repository, body = "accept-1")
            val pending = repository.findDueAccepts(now = RECEIVED_AT, limit = 10).single()
            val nextAttemptAt = RECEIVED_AT.plusSeconds(60)

            repository.postponeAccept(accept = pending, nextAttemptAt = nextAttemptAt)

            assertEquals(listOf(), repository.findDueAccepts(now = RECEIVED_AT, limit = 10))
            assertEquals(1, repository.findDueAccepts(now = nextAttemptAt, limit = 10).single().attempts)
        }
    }

    @Test
    fun `Accept を諦めると成立していないフォローも消える`() {
        withRepository { repository ->
            record(repository, body = "accept-1")
            val pending = repository.findDueAccepts(now = RECEIVED_AT, limit = 10).single()

            repository.giveUpAccept(pending)

            assertEquals(listOf(), repository.findDueAccepts(now = RECEIVED_AT, limit = 10))
            assertNull(repository.findFolloweeUsername(followerActorUri = ALICE.actorUri, followActivityUri = FOLLOW_ID))
        }
    }

    @Test
    fun `Follow の id が一致するときだけ解除する`() {
        withRepository { repository ->
            record(repository, body = "accept-1")

            assertFalse(repository.remove(username = "user1", followerActorUri = ALICE.actorUri, followActivityUri = "other"))
            assertEquals("user1", repository.findFolloweeUsername(followerActorUri = ALICE.actorUri, followActivityUri = FOLLOW_ID))
            assertTrue(repository.remove(username = "user1", followerActorUri = ALICE.actorUri, followActivityUri = FOLLOW_ID))
            assertNull(repository.findFolloweeUsername(followerActorUri = ALICE.actorUri, followActivityUri = FOLLOW_ID))
            assertEquals(listOf(), repository.findDueAccepts(now = RECEIVED_AT, limit = 10))
        }
    }

    @Test
    fun `アクターを消すとフォローと公開鍵も消える`() {
        withRepository { repository ->
            record(repository, body = "accept-1")

            assertEquals(1, repository.removeRemoteActor(ALICE.actorUri))
            assertNull(repository.findPublicKeyPem(ALICE.actorUri))
        }
    }

    @Test
    fun `公開鍵を置き換える`() {
        withRepository { repository ->
            record(repository, body = "accept-1")

            repository.updatePublicKeyPem(actorUri = ALICE.actorUri, publicKeyPem = "new-key")

            assertEquals("new-key", repository.findPublicKeyPem(ALICE.actorUri))
        }
    }

    @Test
    fun `いないアカウント宛のフォローは記録しない`() {
        withRepository { repository ->
            val recorded =
                repository.record(
                    username = "nobody",
                    follower = ALICE,
                    followActivityUri = FOLLOW_ID,
                    receivedAt = RECEIVED_AT,
                    acceptBody = "accept-1",
                    acceptNextAttemptAt = RECEIVED_AT,
                )

            assertFalse(recorded)
            assertNull(repository.findPublicKeyPem(ALICE.actorUri))
        }
    }

    private fun withRepository(block: (FollowerRepository) -> Unit) {
        TestSchema.applyTo(dbPath)
        Accounts.insert(
            databasePath = dbPath,
            username = "user1",
            passwordHash = "hash",
            passwordHashType = "hash-type",
            publicKeyPem = "public-key",
            privateKeyPem = "private-key",
            createdAt = RECEIVED_AT.toString(),
        )
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            block(repositories.followerRepository)
        }
    }

    private fun record(
        repository: FollowerRepository,
        body: String,
    ) {
        assertTrue(
            repository.record(
                username = "user1",
                follower = ALICE,
                followActivityUri = FOLLOW_ID,
                receivedAt = RECEIVED_AT,
                acceptBody = body,
                acceptNextAttemptAt = RECEIVED_AT,
            ),
        )
    }

    private companion object {
        const val FOLLOW_ID = "https://remote.example/users/alice#follows/1"

        val RECEIVED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")

        val ALICE =
            RemoteActorRecord(
                actorUri = "https://remote.example/users/alice",
                inbox = "https://remote.example/users/alice/inbox",
                sharedInbox = "https://remote.example/inbox",
                publicKeyPem = "alice-key",
                profile = RemoteActorProfileRecord(preferredUsername = "alice", displayName = null, profileUrl = null, iconUrl = null),
            )
    }
}
