package net.matsudamper.kmp.activitypub.timeline

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeHomeTimelineStore
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class HomeTimelineServiceTest {
    private val store = FakeHomeTimelineStore()
    private val noteRepository = FakeNoteRepository()
    private val service = HomeTimelineService(store = store, noteRepository = noteRepository, maxSize = 2)

    @Test
    fun `並びが無ければ DB から作り直して新しい順に返す`() {
        val notes = (1..2).map { post(index = it) }

        val page = service.list(account = USER1, after = null, limit = 10)

        assertEquals(notes.reversed(), page.notes)
        assertNull(page.next)
        assertEquals(notes.reversed().map { it.position() }, store.timelines[USER1.id])
    }

    @Test
    fun `持たせる件数より古いページは DB から引く`() {
        val notes = (1..5).map { post(index = it) }

        val first = service.list(account = USER1, after = null, limit = 3)
        val second = service.list(account = USER1, after = first.next, limit = 3)

        assertEquals(notes.reversed().take(3), first.notes)
        assertEquals(notes.reversed().drop(3), second.notes)
        assertNull(second.next)
    }

    @Test
    fun `同じ時刻の投稿がページの境目に来ても落ちも重なりもしない`() {
        val notes = (1..3).map { post(index = it, publishedAt = Instant.ofEpochSecond(100)) }

        val first = service.list(account = USER1, after = null, limit = 2)
        val second = service.list(account = USER1, after = first.next, limit = 2)

        assertEquals(notes.reversed(), first.notes + second.notes)
    }

    @Test
    fun `投稿したものは次に読んだときに並ぶ`() {
        service.list(account = USER1, after = null, limit = 10)
        val note = post(index = 1)

        service.onNotePosted(authorId = USER1.id, position = note.position())

        assertEquals(listOf(note), service.list(account = USER1, after = null, limit = 10).notes)
    }

    @Test
    fun `消された投稿は並びに残っていても返さない`() {
        val notes = (1..2).map { post(index = it) }
        service.list(account = USER1, after = null, limit = 10)

        noteRepository.delete(notes[1].publicId)

        assertEquals(listOf(notes[0]), service.list(account = USER1, after = null, limit = 10).notes)
    }

    private fun post(
        index: Int,
        publishedAt: Instant = Instant.ofEpochSecond(index.toLong()),
    ): Note {
        val note =
            Note(
                publicId = PublicNoteId("note-$index"),
                username = USER1.username,
                contentHtml = "<p>$index</p>",
                publishedAt = publishedAt,
            )
        noteRepository.notes += note
        return note
    }

    private fun Note.position(): NotePosition = NotePosition(publishedAt = publishedAt, publicId = publicId)

    private companion object {
        val USER1 = Account(id = AccountId(1), username = "user1", createdAt = Instant.EPOCH)
    }
}
