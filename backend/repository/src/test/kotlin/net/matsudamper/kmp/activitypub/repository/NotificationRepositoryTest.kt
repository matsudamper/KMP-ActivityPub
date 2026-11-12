package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class NotificationRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-notification-test")
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

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `お気に入りとブーストを受け取ると投稿者に新しい順の通知が残る`() = withRepositories { repositories, author ->
        repositories.favouriteRepository.add(NOTE_1, actor(ALICE), NOW)
        repositories.boostRepository.add(NOTE_1, actor(BOB), "$BOB/announces/1", NOW.plusSeconds(1))
        // 同じ相手の押し直しでは通知を増やさない
        repositories.favouriteRepository.add(NOTE_1, actor(ALICE), NOW.plusSeconds(2))

        val notifications = repositories.notificationRepository.page(author, null, 10)

        assertEquals(listOf(NotificationKind.BOOST to BOB, NotificationKind.FAVOURITE to ALICE), notifications.map { it.kind to it.actor.actorUri })
        assertEquals(listOf(author, author), notifications.map { it.accountId })
        assertEquals("alice", notifications.last().actor.username)
        val post = repositories.timelineRepository.hydrate(author, notifications.map { it.noteMember }.distinct()).single()
        assertEquals(NOTE_1, post.localPublicId)
        assertEquals(notifications.first(), repositories.notificationRepository.find(NotificationKind.BOOST, NOTE_1, BOB))
    }

    @Test
    fun `続きは前のページの最後より古い通知だけを返す`() = withRepositories { repositories, author ->
        repositories.favouriteRepository.add(NOTE_1, actor(ALICE), NOW)
        repositories.favouriteRepository.add(NOTE_1, actor(BOB), NOW)
        repositories.boostRepository.add(NOTE_1, actor(BOB), "$BOB/announces/1", NOW)

        val first = repositories.notificationRepository.page(author, null, 2)
        val second = repositories.notificationRepository.page(author, first.last().id, 2)

        assertEquals(2, first.size)
        assertEquals(listOf(NotificationKind.FAVOURITE to ALICE), second.map { it.kind to it.actor.actorUri })
        assertTrue(repositories.notificationRepository.page(author, second.last().id, 2).isEmpty())
    }

    @Test
    fun `取り消されたお気に入りとブーストの通知は消える`() = withRepositories { repositories, author ->
        repositories.favouriteRepository.add(NOTE_1, actor(ALICE), NOW)
        repositories.boostRepository.add(NOTE_1, actor(ALICE), "$ALICE/announces/1", NOW)
        repositories.favouriteRepository.add(NOTE_1, actor(BOB), NOW)

        repositories.favouriteRepository.remove(NOTE_1, ALICE)
        assertNull(repositories.notificationRepository.find(NotificationKind.FAVOURITE, NOTE_1, ALICE))
        assertNotNull(repositories.notificationRepository.find(NotificationKind.BOOST, NOTE_1, ALICE))

        repositories.boostRepository.remove(ALICE, "$ALICE/announces/1", NOW)
        assertEquals(listOf(NotificationKind.FAVOURITE to BOB), repositories.notificationRepository.page(author, null, 10).map { it.kind to it.actor.actorUri })

        repositories.favouriteRepository.removeActor(BOB)
        assertTrue(repositories.notificationRepository.page(author, null, 10).isEmpty())
    }

    private fun withRepositories(block: (Repositories, AccountId) -> Unit) {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.deliveryQueueRepository.enqueueNote(
                NotePost(
                    username = "user1",
                    requestId = "request-note-1",
                    publicId = NOTE_1,
                    contentHtml = "<p>hello</p>",
                    publishedAt = NOW,
                    body = "create-note-1",
                    inboxes = listOf(),
                    images = listOf(),
                ),
            )
            block(repositories, checkNotNull(repositories.accountRepository.findByUsername("user1")).id)
        }
    }

    private fun actor(actorUri: String): RemoteActorRecord = RemoteActorRecord(
        actorUri = actorUri,
        inbox = "$actorUri/inbox",
        sharedInbox = null,
        publicKeyPem = "key-$actorUri",
        profile = RemoteActorProfileRecord(actorUri.substringAfterLast('/'), null, null, null, null, listOf()),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T00:00:00Z")
        val NOTE_1 = PublicNoteId("note-1")
        const val ALICE = "https://remote.example/users/alice"
        const val BOB = "https://remote.example/users/bob"
    }
}
