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
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class StampRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-stamp-test")
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
    fun `多く押された絵文字から並べ、画像の違うカスタム絵文字は分ける`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val stamps = repositories.stampRepository
            assertTrue(stamps.put(NOTE_1, actor(ALICE), "👍", null, NOW))
            assertTrue(stamps.put(NOTE_1, actor(ALICE), ":kotlin:", "https://a.example/kotlin.png", NOW.plusSeconds(1)))
            assertTrue(stamps.put(NOTE_1, actor(BOB), "👍", null, NOW.plusSeconds(2)))
            assertTrue(stamps.put(NOTE_1, actor(BOB), ":kotlin:", "https://b.example/kotlin.png", NOW.plusSeconds(3)))

            assertEquals(
                mapOf(
                    NOTE_1 to listOf(
                        StampCount("👍", null, 2),
                        StampCount(":kotlin:", "https://a.example/kotlin.png", 1),
                        StampCount(":kotlin:", "https://b.example/kotlin.png", 1),
                    ),
                    NOTE_2 to listOf(),
                ),
                stamps.counts(setOf(NOTE_1, NOTE_2)),
            )
            assertEquals("key-$ALICE", stamps.findPublicKeyPem(ALICE))
        }
    }

    @Test
    fun `同じ絵文字を押し直しても数は増えない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val stamps = repositories.stampRepository
            stamps.put(NOTE_1, actor(ALICE), "👍", null, NOW)
            stamps.put(NOTE_1, actor(ALICE), "👍", null, NOW)

            assertEquals(listOf(StampCount("👍", null, 1)), stamps.counts(setOf(NOTE_1)).getValue(NOTE_1))
        }
    }

    @Test
    fun `無い投稿へのスタンプは記録しない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertFalse(repositories.stampRepository.put(NOTE_1, actor(ALICE), "👍", null, NOW))
            assertNull(repositories.stampRepository.findPublicKeyPem(ALICE))
        }
    }

    @Test
    fun `取り消しは押した相手のその絵文字だけを消す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val stamps = repositories.stampRepository
            stamps.put(NOTE_1, actor(ALICE), "👍", null, NOW)
            stamps.put(NOTE_1, actor(ALICE), "🎉", null, NOW.plusSeconds(1))
            stamps.put(NOTE_1, actor(BOB), "👍", null, NOW.plusSeconds(2))

            assertTrue(stamps.remove(NOTE_1, ALICE, "👍"))
            assertFalse(stamps.remove(NOTE_1, ALICE, "👍"))
            assertEquals(listOf(StampCount("🎉", null, 1), StampCount("👍", null, 1)), stamps.counts(setOf(NOTE_1)).getValue(NOTE_1))

            assertEquals(1, stamps.removeActor(BOB))
            assertEquals(listOf(StampCount("🎉", null, 1)), stamps.counts(setOf(NOTE_1)).getValue(NOTE_1))
        }
    }

    @Test
    fun `タイムラインのこちらの投稿にスタンプが付く`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            repositories.stampRepository.put(NOTE_1, actor(ALICE), "👍", null, NOW)
            val account = checkNotNull(repositories.accountRepository.findByUsername("user1"))
            val position = checkNotNull(repositories.timelineRepository.localPosition(NOTE_1.value))

            val post = repositories.timelineRepository.hydrate(account.id, listOf(position.member)).single()

            assertEquals(listOf(StampCount("👍", null, 1)), post.stamps)
        }
    }

    private fun Repositories.postNote(publicId: String) {
        deliveryQueueRepository.enqueueNote(
            NotePost(
                username = "user1",
                requestId = "request-$publicId",
                publicId = PublicNoteId(publicId),
                contentHtml = "<p>hello</p>",
                publishedAt = NOW,
                body = "create-$publicId",
                inboxes = listOf(),
                images = listOf(),
            ),
        )
    }

    private fun actor(actorUri: String): RemoteActorRecord =
        RemoteActorRecord(
            actorUri = actorUri,
            inbox = "$actorUri/inbox",
            sharedInbox = null,
            publicKeyPem = "key-$actorUri",
            profile = RemoteActorProfileRecord(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")
        val NOTE_1 = PublicNoteId("note-1")
        val NOTE_2 = PublicNoteId("note-2")
        const val ALICE = "https://remote.example/users/alice"
        const val BOB = "https://remote.example/users/bob"
    }
}
