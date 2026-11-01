package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class FollowerRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-follower-test")
    private val dbPath: Path = tempDir.resolve("test.db")

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
    fun `Accept を送れるまではフォロワーに数えない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            val queue = repositories.deliveryQueueRepository
            assertTrue(followers.recordFollow(FOLLOWER))

            assertEquals(0, followers.countAcceptedFollowers("user1"))
            assertEquals(listOf(), followers.listAcceptedFollowerActorUris(username = "user1", after = null, limit = 10))

            val claimed = queue.claim(now = NOW, limit = 10).single()
            assertEquals("user1", claimed.username)
            assertEquals(FOLLOWER.inbox, claimed.inbox)
            assertEquals(ACCEPT_BODY, claimed.body)

            queue.markDelivered(claimed.id, deliveredAt = NOW)

            assertEquals(1, followers.countAcceptedFollowers("user1"))
            assertEquals(
                listOf(FOLLOWER.actorUri),
                followers.listAcceptedFollowerActorUris(username = "user1", after = null, limit = 10),
            )
            assertEquals(listOf(FOLLOWER.sharedInbox), followers.findDeliveryInboxes("user1"))
            assertEquals(0, queue.recoverDelivering(now = NOW))
            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
        }
    }

    @Test
    fun `いないアカウントへのフォローは記録しない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertFalse(repositories.followerRepository.recordFollow(FOLLOWER, username = "nobody"))
            assertNull(repositories.followerRepository.findPublicKeyPem(FOLLOWER.actorUri))
        }
    }

    @Test
    fun `同じ相手から Follow を受け直すと Accept を置き換える`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            followers.recordFollow(FOLLOWER)
            followers.recordFollow(FOLLOWER, followActivityUri = "https://remote.example/follows/2", acceptBody = "accept-2")

            val claimed = repositories.deliveryQueueRepository.claim(now = NOW, limit = 10).single()
            assertEquals("accept-2", claimed.body)
            assertEquals("user1", followers.findFolloweeUsername(FOLLOWER.actorUri, "https://remote.example/follows/2"))
            assertNull(followers.findFolloweeUsername(FOLLOWER.actorUri, FOLLOW_ACTIVITY_URI))
        }
    }

    @Test
    fun `置き換えられた古い Accept を送れてもフォローは成立させない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            val queue = repositories.deliveryQueueRepository
            followers.recordFollow(FOLLOWER)
            val oldClaimed = queue.claim(now = NOW, limit = 10).single()
            followers.recordFollow(FOLLOWER, followActivityUri = "https://remote.example/follows/2", acceptBody = "accept-2")

            queue.markDelivered(oldClaimed.id, deliveredAt = NOW)

            assertEquals(0, followers.countAcceptedFollowers("user1"))
            assertEquals("accept-2", queue.claim(now = NOW, limit = 10).single().body)
        }
    }

    @Test
    fun `置き換えられた古い Accept を諦めても新しいフォローは消さない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            val queue = repositories.deliveryQueueRepository
            followers.recordFollow(FOLLOWER)
            val oldClaimed = queue.claim(now = NOW, limit = 10).single()
            followers.recordFollow(FOLLOWER, followActivityUri = "https://remote.example/follows/2", acceptBody = "accept-2")

            queue.giveUp(oldClaimed.id, error = "410")

            assertEquals("user1", followers.findFolloweeUsername(FOLLOWER.actorUri, "https://remote.example/follows/2"))
            assertEquals("accept-2", queue.claim(now = NOW, limit = 10).single().body)
        }
    }

    @Test
    fun `送り直しの時刻が来るまでは配信対象に出さない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            repositories.followerRepository.recordFollow(FOLLOWER)
            val claimed = queue.claim(now = NOW, limit = 10).single()

            queue.scheduleRetry(claimed.id, nextAttemptAt = NOW.plusSeconds(60), error = "503")

            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
            assertEquals(2, queue.claim(now = NOW.plusSeconds(60), limit = 10).single().attempts)
        }
    }

    @Test
    fun `送っている途中で止まった Accept は起動時に戻す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            repositories.followerRepository.recordFollow(FOLLOWER)
            queue.claim(now = NOW, limit = 10)

            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
            assertEquals(1, queue.recoverDelivering(now = NOW))
            assertEquals(ACCEPT_BODY, queue.claim(now = NOW, limit = 10).single().body)
        }
    }

    @Test
    fun `Accept を諦めたらフォローも消す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            val queue = repositories.deliveryQueueRepository
            followers.recordFollow(FOLLOWER)
            val claimed = queue.claim(now = NOW, limit = 10).single()

            queue.giveUp(claimed.id, error = "410")

            assertNull(followers.findFolloweeUsername(FOLLOWER.actorUri, FOLLOW_ACTIVITY_URI))
            assertEquals(0, queue.recoverDelivering(now = NOW))
            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
        }
    }

    @Test
    fun `フォローを解除しても公開鍵は残す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            followers.recordFollow(FOLLOWER)
            val queue = repositories.deliveryQueueRepository
            queue.markDelivered(queue.claim(now = NOW, limit = 10).single().id, deliveredAt = NOW)

            assertFalse(followers.remove(username = "user1", followerActorUri = FOLLOWER.actorUri, followActivityUri = "https://remote.example/other"))
            assertTrue(followers.remove(username = "user1", followerActorUri = FOLLOWER.actorUri, followActivityUri = FOLLOW_ACTIVITY_URI))

            assertEquals(0, followers.countAcceptedFollowers("user1"))
            assertEquals(FOLLOWER.publicKeyPem, followers.findPublicKeyPem(FOLLOWER.actorUri))
        }
    }

    @Test
    fun `アクターごと消すとフォローも鍵も消える`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            followers.recordFollow(FOLLOWER)

            assertEquals(1, followers.removeRemoteActor(FOLLOWER.actorUri))

            assertNull(followers.findPublicKeyPem(FOLLOWER.actorUri))
            assertEquals(listOf(), repositories.deliveryQueueRepository.claim(now = NOW, limit = 10))
        }
    }

    @Test
    fun `成立したフォロワーだけを成立した新しい順にプロフィール付きでページごとに返す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val followers = repositories.followerRepository
            val queue = repositories.deliveryQueueRepository
            val older = FOLLOWER.copy(actorUri = "https://remote.example/users/bob", inbox = "https://remote.example/users/bob/inbox")
            val pending = FOLLOWER.copy(actorUri = "https://remote.example/users/carol", inbox = "https://remote.example/users/carol/inbox")
            followers.recordFollow(FOLLOWER, followActivityUri = "https://remote.example/follows/alice")
            followers.recordFollow(older, followActivityUri = "https://remote.example/follows/bob")
            queue.claim(now = NOW, limit = 10).forEach { claimed ->
                val deliveredAt = if (claimed.inbox == older.inbox) NOW else NOW.plusSeconds(1)
                queue.markDelivered(claimed.id, deliveredAt = deliveredAt)
            }
            followers.recordFollow(pending, followActivityUri = "https://remote.example/follows/carol")

            val firstPage = followers.listAcceptedFollowers(username = "user1", after = null, limit = 1)
            assertEquals(listOf(FOLLOWER.actorUri), firstPage.map { it.actorUri })
            assertEquals("alice", firstPage.single().preferredUsername)
            assertEquals("Alice", firstPage.single().displayName)

            val last = firstPage.single()
            val secondPage = followers.listAcceptedFollowers(
                username = "user1",
                after = ActorListPosition(listedAt = last.listedAt, actorUri = last.actorUri),
                limit = 10,
            )
            assertEquals(listOf(older.actorUri), secondPage.map { it.actorUri })
        }
    }

    private fun FollowerRepository.recordFollow(
        follower: RemoteActorRecord,
        username: String = "user1",
        followActivityUri: String = FOLLOW_ACTIVITY_URI,
        acceptBody: String = ACCEPT_BODY,
    ): Boolean = record(
        username = username,
        follower = follower,
        followActivityUri = followActivityUri,
        receivedAt = NOW,
        acceptBody = acceptBody,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")

        const val FOLLOW_ACTIVITY_URI = "https://remote.example/follows/1"

        const val ACCEPT_BODY = "accept-1"

        val FOLLOWER = RemoteActorRecord(
            actorUri = "https://remote.example/users/alice",
            inbox = "https://remote.example/users/alice/inbox",
            sharedInbox = "https://remote.example/inbox",
            publicKeyPem = "remote-public-key",
            profile = RemoteActorProfileRecord(
                preferredUsername = "alice",
                displayName = "Alice",
                profileUrl = "https://remote.example/@alice",
                iconUrl = null,
            ),
        )
    }
}
