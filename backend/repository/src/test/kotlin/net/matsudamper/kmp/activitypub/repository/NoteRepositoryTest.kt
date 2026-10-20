package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class NoteRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-note-test")
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
    fun `投稿を記録して宛先ごとに積む`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            assertEquals(2, queue.enqueueNote(notePost(publicId = "note-1", inboxes = listOf(INBOX_A, INBOX_B, INBOX_A))))

            val note = repositories.noteRepository.find(PublicNoteId("note-1"))
            assertEquals("user1", note?.username)
            assertEquals(1, repositories.noteRepository.count("user1"))

            val claimed = queue.claim(now = NOW, limit = 10)
            assertEquals(setOf(INBOX_A, INBOX_B), claimed.map { it.inbox }.toSet())
            assertEquals(listOf(1, 1), claimed.map { it.attempts })
            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
        }
    }

    @Test
    fun `宛先が無くても投稿は記録する`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(0, repositories.deliveryQueueRepository.enqueueNote(notePost(publicId = "note-1", inboxes = listOf())))

            assertEquals(1, repositories.noteRepository.count("user1"))
        }
    }

    @Test
    fun `いないアカウントの投稿は記録しない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertNull(repositories.deliveryQueueRepository.enqueueNote(notePost(publicId = "note-1", inboxes = listOf(INBOX_A)).copy(username = "missing")))

            assertNull(repositories.noteRepository.find(PublicNoteId("note-1")))
        }
    }

    @Test
    fun `送り直しは時刻が来るまで取り出さない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.enqueueNote(notePost(publicId = "note-1", inboxes = listOf(INBOX_A)))
            val claimed = queue.claim(now = NOW, limit = 10).single()

            val retryAt = NOW.plusSeconds(60)
            queue.scheduleRetry(claimed.id, nextAttemptAt = retryAt, error = "503")

            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
            assertEquals(2, queue.claim(now = retryAt, limit = 10).single().attempts)
        }
    }

    @Test
    fun `送れた行と諦めた行は取り出さない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.enqueueNote(notePost(publicId = "note-1", inboxes = listOf(INBOX_A, INBOX_B)))
            val claimed = queue.claim(now = NOW, limit = 10)
            queue.markDelivered(claimed[0].id, deliveredAt = NOW)
            queue.giveUp(claimed[1].id, error = "410")

            assertEquals(0, queue.recoverDelivering(now = NOW))
            assertEquals(listOf(), queue.claim(now = NOW.plusSeconds(3600), limit = 10))
        }
    }

    @Test
    fun `送っている途中で止まった行は起動時に戻す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.enqueueNote(notePost(publicId = "note-1", inboxes = listOf(INBOX_A)))
            queue.claim(now = NOW, limit = 10)

            assertEquals(1, queue.recoverDelivering(now = NOW))
            assertEquals(INBOX_A, queue.claim(now = NOW, limit = 10).single().inbox)
        }
    }

    @Test
    fun `投稿を消すとまだ送っていない配信も消える`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.enqueueNote(notePost(publicId = "note-1", inboxes = listOf(INBOX_A)))

            repositories.noteRepository.delete(PublicNoteId("note-1"))

            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
        }
    }

    @Test
    fun `投稿は新しい順に並び、位置の続きから返す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.enqueueNote(notePost(publicId = "note-1", inboxes = listOf()))
            queue.enqueueNote(notePost(publicId = "note-2", inboxes = listOf()))
            queue.enqueueNote(notePost(publicId = "note-3", inboxes = listOf()).copy(publishedAt = NOW.plusSeconds(1)))
            val notes = repositories.noteRepository

            val firstPage = notes.list(username = "user1", after = null, limit = 2)
            assertEquals(listOf("note-3", "note-2"), firstPage.map { it.publicId.value })

            val secondPage = notes.listPositions(
                username = "user1",
                after = NotePosition(publishedAt = firstPage.last().publishedAt, publicId = firstPage.last().publicId),
                limit = 2,
            )
            assertEquals(listOf("note-1"), secondPage.map { it.publicId.value })
            assertEquals(mapOf("USER1" to 3L, "other" to 0L), notes.counts(setOf("USER1", "other")))
        }
    }

    private fun notePost(
        publicId: String,
        inboxes: List<String>,
    ): NotePost =
        NotePost(
            username = "user1",
            publicId = PublicNoteId(publicId),
            contentHtml = "<p>hello</p>",
            publishedAt = NOW,
            body = "create-$publicId",
            inboxes = inboxes,
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")
        const val INBOX_A = "https://a.example/inbox"
        const val INBOX_B = "https://b.example/inbox"
    }
}
