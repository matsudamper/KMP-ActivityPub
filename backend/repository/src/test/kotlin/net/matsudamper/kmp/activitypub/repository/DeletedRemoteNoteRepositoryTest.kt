package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId

class DeletedRemoteNoteRepositoryTest {
    @Test
    fun pageReturnsOnlyDeletedNotesNewestFirst() = withRepositories { repositories, alice ->
        val timeline = repositories.timelineRepository
        listOf("1", "2", "3").forEach { assertNotNull(timeline.receive(note(it))) }
        timeline.deleteRemote(ACTOR, "$ACTOR/notes/1", DELETED_AT)
        timeline.deleteRemote(ACTOR, "$ACTOR/notes/3", DELETED_AT)

        val deleted = repositories.deletedRemoteNoteRepository
        val firstPage = deleted.page(beforeId = null, limit = 1)
        assertEquals(listOf("$ACTOR/notes/3"), firstPage.map { it.objectUri })
        assertEquals("remote", firstPage.single().actorName)
        assertEquals(DELETED_AT, firstPage.single().deletedAt)
        assertEquals(listOf("$ACTOR/notes/1"), deleted.page(beforeId = firstPage.single().id, limit = 10).map { it.objectUri })
        assertEquals(3, timeline.page(alice, null, null, 10).size)
    }

    @Test
    fun purgeRemovesOnlyDeletedNotesFromTimeline() = withRepositories { repositories, alice ->
        val timeline = repositories.timelineRepository
        val kept = assertNotNull(timeline.receive(note("1")))
        assertNotNull(timeline.receive(note("2")))
        assertNotNull(timeline.receive(note("3")))
        timeline.deleteRemote(ACTOR, "$ACTOR/notes/2", DELETED_AT)
        timeline.deleteRemote(ACTOR, "$ACTOR/notes/3", DELETED_AT)

        val deleted = repositories.deletedRemoteNoteRepository
        val keptId = checkNotNull(kept.member.removePrefix("r").toLongOrNull())
        assertFalse(deleted.purge(keptId))
        val (newest, older) = deleted.page(beforeId = null, limit = 10)
        assertTrue(deleted.purge(newest.id))
        assertFalse(deleted.purge(newest.id))
        assertEquals(listOf(older.id), deleted.page(beforeId = null, limit = 10).map { it.id })

        assertEquals(1, deleted.purgeAll())
        assertTrue(deleted.page(beforeId = null, limit = 10).isEmpty())
        assertEquals(listOf(kept), timeline.page(alice, null, null, 10))
    }

    private fun note(id: String): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", language = null, visibility = "public",
        localRecipients = setOf(), inReplyTo = null, quoteUri = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW, editedAt = null,
        images = listOf(),
        emojis = listOf(),
        linkHref = null,
        poll = null,
    )

    private fun withRepositories(block: (Repositories, AccountId) -> Unit) {
        val directory = createTempDirectory("deleted-remote-note-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            Accounts.insert(path, "alice", "hash", "type", "public-key", "private-key", NOW.toString())
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf(), null, listOf())),
                    NOW,
                )
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                repositories.followingRepository.add(alice, ACTOR, NOW)
                block(repositories, alice)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ACTOR = "https://remote.example/actor"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
        val DELETED_AT: Instant = Instant.parse("2026-11-08T12:00:00Z")
    }
}
