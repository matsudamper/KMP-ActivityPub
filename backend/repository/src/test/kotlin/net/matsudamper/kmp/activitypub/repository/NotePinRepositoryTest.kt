package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class NotePinRepositoryTest {
    @Test
    fun pinnedNotesAreListedNewestPinFirst() = withRepository { repositories, alice, _ ->
        postLocalNote(repositories, username = "alice", publicId = "first")
        postLocalNote(repositories, username = "alice", publicId = "second")
        val pins = repositories.notePinRepository

        assertEquals(PinNoteResult.Pinned, pins.pin(alice, PublicNoteId("first"), NOW, MAX_PINS))
        assertEquals(PinNoteResult.Pinned, pins.pin(alice, PublicNoteId("second"), NOW.plusSeconds(1), MAX_PINS))
        assertEquals(PinNoteResult.Pinned, pins.pin(alice, PublicNoteId("first"), NOW.plusSeconds(2), MAX_PINS))

        assertEquals(listOf(PublicNoteId("second"), PublicNoteId("first")), pins.pinnedNotes("alice"))
    }

    @Test
    fun noteOfAnotherAccountCannotBePinnedOrUnpinned() = withRepository { repositories, alice, bob ->
        postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val pins = repositories.notePinRepository

        assertEquals(PinNoteResult.NotFound, pins.pin(alice, PublicNoteId("bob-note"), NOW, MAX_PINS))
        assertEquals(PinNoteResult.NotFound, pins.pin(alice, PublicNoteId("missing"), NOW, MAX_PINS))
        assertEquals(PinNoteResult.Pinned, pins.pin(bob, PublicNoteId("bob-note"), NOW, MAX_PINS))
        assertFalse(pins.unpin(alice, PublicNoteId("bob-note")))

        assertEquals(listOf(PublicNoteId("bob-note")), pins.pinnedNotes("bob"))
        assertEquals(listOf(), pins.pinnedNotes("alice"))
    }

    @Test
    fun pinsBeyondTheLimitAreRefusedUntilOneIsUnpinned() = withRepository { repositories, alice, bob ->
        postLocalNote(repositories, username = "alice", publicId = "first")
        postLocalNote(repositories, username = "alice", publicId = "second")
        postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val pins = repositories.notePinRepository
        assertEquals(PinNoteResult.Pinned, pins.pin(bob, PublicNoteId("bob-note"), NOW, 1))

        assertEquals(PinNoteResult.Pinned, pins.pin(alice, PublicNoteId("first"), NOW, 1))
        assertEquals(PinNoteResult.TooManyPins, pins.pin(alice, PublicNoteId("second"), NOW, 1))

        assertTrue(pins.unpin(alice, PublicNoteId("first")))
        assertFalse(pins.unpin(alice, PublicNoteId("first")))
        assertEquals(PinNoteResult.Pinned, pins.pin(alice, PublicNoteId("second"), NOW, 1))
        assertEquals(listOf(PublicNoteId("second")), pins.pinnedNotes("alice"))
    }

    @Test
    fun pinnedAmongReturnsOnlyPinnedNotesOfTheGivenOnes() = withRepository { repositories, alice, bob ->
        postLocalNote(repositories, username = "alice", publicId = "pinned")
        postLocalNote(repositories, username = "alice", publicId = "not-pinned")
        postLocalNote(repositories, username = "bob", publicId = "bob-pinned")
        repositories.notePinRepository.pin(alice, PublicNoteId("pinned"), NOW, MAX_PINS)
        repositories.notePinRepository.pin(bob, PublicNoteId("bob-pinned"), NOW, MAX_PINS)

        assertEquals(
            setOf(PublicNoteId("pinned")),
            repositories.notePinRepository.pinnedAmong(setOf(PublicNoteId("pinned"), PublicNoteId("not-pinned"), PublicNoteId("missing"))),
        )
        assertEquals(setOf(), repositories.notePinRepository.pinnedAmong(setOf()))
    }

    @Test
    fun deletedNoteIsNoLongerPinned() = withRepository { repositories, alice, _ ->
        postLocalNote(repositories, username = "alice", publicId = "first")
        repositories.notePinRepository.pin(alice, PublicNoteId("first"), NOW, MAX_PINS)

        repositories.noteRepository.delete(PublicNoteId("first"))

        assertEquals(listOf(), repositories.notePinRepository.pinnedNotes("alice"))
    }

    private fun postLocalNote(repositories: Repositories, username: String, publicId: String) {
        repositories.deliveryQueueRepository.enqueueNote(
            NotePost(
                username = username,
                requestId = "request-$publicId",
                publicId = PublicNoteId(publicId),
                contentHtml = "<p>本文</p>",
                publishedAt = NOW,
                images = listOf(),
                body = "create",
                inboxes = listOf(),
            ),
        )
    }

    private fun withRepository(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("note-pin-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                block(repositories, alice, bob)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val MAX_PINS = 5
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
