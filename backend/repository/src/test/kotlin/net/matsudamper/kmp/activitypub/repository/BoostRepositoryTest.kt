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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class BoostRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-boost-test")
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
    fun `同じ相手がブーストし直しても数は増えない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val boosts = repositories.boostRepository

            assertTrue(boosts.add(NOTE_1, actor(ALICE), "$ALICE/announces/1", NOW))
            assertFalse(boosts.add(NOTE_1, actor(ALICE), "$ALICE/announces/2", NOW))
            assertTrue(boosts.add(NOTE_1, actor(BOB), "$BOB/announces/1", NOW))

            assertEquals(mapOf(NOTE_1 to 2L, NOTE_2 to 0L), boosts.counts(setOf(NOTE_1, NOTE_2)))
            assertEquals("key-$ALICE", boosts.findPublicKeyPem(ALICE))
        }
    }

    @Test
    fun `無い投稿へのブーストは記録しない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertFalse(repositories.boostRepository.add(NOTE_1, actor(ALICE), "$ALICE/announces/1", NOW))
            assertNull(repositories.boostRepository.findPublicKeyPem(ALICE))
        }
    }

    @Test
    fun `取り消しは本人の Announce の id のものだけ消す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val boosts = repositories.boostRepository
            boosts.add(NOTE_1, actor(ALICE), "$ALICE/announces/1", NOW)
            boosts.add(NOTE_1, actor(BOB), "$BOB/announces/1", NOW)

            assertNull(boosts.remove(BOB, "$ALICE/announces/1", NOW))
            assertNull(boosts.remove(ALICE, "$ALICE/announces/2", NOW))
            assertEquals(BoostRemoval(undoneTimelineMember = null), boosts.remove(ALICE, "$ALICE/announces/1", NOW))

            assertEquals(mapOf(NOTE_1 to 1L), boosts.counts(setOf(NOTE_1)))
        }
    }

    @Test
    fun `消えた相手のブーストを消す`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.postNote("note-1")
            val boosts = repositories.boostRepository
            boosts.add(NOTE_1, actor(ALICE), "$ALICE/announces/1", NOW)
            boosts.add(NOTE_1, actor(BOB), "$BOB/announces/1", NOW)

            assertEquals(1, boosts.removeActor(ALICE))

            assertEquals(mapOf(NOTE_1 to 1L), boosts.counts(setOf(NOTE_1)))
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
            profile = RemoteActorProfileRecord(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null, headerUrl = null, emojis = listOf()),
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")
        val NOTE_1 = PublicNoteId("note-1")
        val NOTE_2 = PublicNoteId("note-2")
        const val ALICE = "https://remote.example/users/alice"
        const val BOB = "https://remote.example/users/bob"
    }
}
