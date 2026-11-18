package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class NoteCountRepositoryTest {
    @Test
    fun localAndReadableRemoteRepliesAreCounted() = withRepository { repositories ->
        repositories.deliveryQueueRepository.enqueueNote(notePost(publicId = "reply").copy(inReplyTo = TARGET))
        repositories.timelineRepository.receive(note(id = "public", visibility = "public", inReplyTo = TARGET))
        repositories.timelineRepository.receive(note(id = "unlisted", visibility = "unlisted", inReplyTo = TARGET))
        repositories.timelineRepository.receive(note(id = "direct", visibility = "direct", inReplyTo = TARGET))
        repositories.timelineRepository.receive(note(id = "deleted", visibility = "public", inReplyTo = TARGET))
        repositories.timelineRepository.deleteRemote(ACTOR, "$ACTOR/notes/deleted", NOW)

        assertEquals(
            mapOf(TARGET to 3L, OTHER to 0L),
            repositories.noteCountRepository.knownReplyCounts(setOf(TARGET, OTHER)),
        )
    }

    @Test
    fun reportedCountsAreSavedOnlyForStoredRemoteNotes() = withRepository { repositories ->
        repositories.timelineRepository.receive(note(id = "stored", visibility = "public", inReplyTo = null))
        val stored = "$ACTOR/notes/stored"
        val counts = ReportedNoteCounts(replyCount = 5, favouriteCount = null, boostCount = 2, fetchedAt = NOW)

        assertEquals(mapOf(), repositories.noteCountRepository.reportedCounts(setOf(stored)))
        assertTrue(repositories.noteCountRepository.saveReportedCounts(stored, counts))
        assertFalse(repositories.noteCountRepository.saveReportedCounts(OTHER, counts))
        assertEquals(mapOf(stored to counts), repositories.noteCountRepository.reportedCounts(setOf(stored, OTHER)))
    }

    private fun notePost(publicId: String): NotePost = NotePost(
        username = "alice",
        requestId = "request-$publicId",
        publicId = PublicNoteId(publicId),
        contentHtml = "<p>本文</p>",
        summary = null,
        publishedAt = NOW,
        images = listOf(),
        body = "create",
        inboxes = listOf(),
        inReplyTo = null,
        mentions = listOf(),
        quoteUri = null,
        quoteInbox = null,
    )

    private fun note(id: String, visibility: String, inReplyTo: String?): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", language = null, visibility = visibility,
        localRecipients = setOf("alice"), inReplyTo = inReplyTo, quoteUri = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW, editedAt = null,
        images = listOf(),
        emojis = listOf(),
    )

    private fun withRepository(block: (Repositories) -> Unit) {
        val directory = createTempDirectory("note-count-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            Accounts.insert(path, "alice", "hash", "type", "public-key", "private-key", NOW.toString())
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf())),
                    NOW,
                )
                block(repositories)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ACTOR = "https://remote.example/actor"
        const val TARGET = "https://local.example/notes/target"
        const val OTHER = "https://remote.example/actor/notes/other"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
