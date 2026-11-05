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
class FavouriteRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-favourite-test")
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
    fun `同じ相手が押し直しても数は増えない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val favourites = repositories.favouriteRepository

            assertTrue(favourites.add(NOTE_1, actor(ALICE), NOW))
            assertFalse(favourites.add(NOTE_1, actor(ALICE), NOW))
            assertTrue(favourites.add(NOTE_1, actor(BOB), NOW))

            assertEquals(mapOf(NOTE_1 to 2L, NOTE_2 to 0L), favourites.counts(setOf(NOTE_1, NOTE_2)))
            assertEquals("key-$ALICE", favourites.findPublicKeyPem(ALICE))
        }
    }

    @Test
    fun `無い投稿へのお気に入りは記録しない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertFalse(repositories.favouriteRepository.add(NOTE_1, actor(ALICE), NOW))
            assertNull(repositories.favouriteRepository.findPublicKeyPem(ALICE))
        }
    }

    @Test
    fun `取り消しは押した相手の分だけを消す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val favourites = repositories.favouriteRepository
            favourites.add(NOTE_1, actor(ALICE), NOW)
            favourites.add(NOTE_1, actor(BOB), NOW)

            assertTrue(favourites.remove(NOTE_1, ALICE))
            assertFalse(favourites.remove(NOTE_1, ALICE))
            assertEquals(mapOf(NOTE_1 to 1L), favourites.counts(setOf(NOTE_1)))

            assertEquals(1, favourites.removeActor(BOB))
            assertEquals(mapOf(NOTE_1 to 0L), favourites.counts(setOf(NOTE_1)))
        }
    }

    @Test
    fun `タイムラインのこちらの投稿にお気に入りの数と公開 id が付く`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            repositories.favouriteRepository.add(NOTE_1, actor(ALICE), NOW)
            val account = checkNotNull(repositories.accountRepository.findByUsername("user1"))
            val position = checkNotNull(repositories.timelineRepository.localPosition(NOTE_1.value))

            val post = repositories.timelineRepository.hydrate(account.id, listOf(position.member)).single()

            assertEquals(1L, post.favouriteCount)
            assertEquals(TimelineNoteRef.Local(NOTE_1), post.note)
        }
    }

    @Test
    fun `先に届いた取り消しは期限まで覚えておく`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val favourites = repositories.favouriteRepository
            favourites.rememberEarlyUndone(ALICE, "$ALICE/likes/1", expiresAt = NOW.plusSeconds(60))

            assertTrue(favourites.isEarlyUndone(ALICE, "$ALICE/likes/1", now = NOW))
            assertFalse(favourites.isEarlyUndone(BOB, "$ALICE/likes/1", now = NOW))
            assertFalse(favourites.isEarlyUndone(ALICE, "$ALICE/likes/1", now = NOW.plusSeconds(60)))
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
