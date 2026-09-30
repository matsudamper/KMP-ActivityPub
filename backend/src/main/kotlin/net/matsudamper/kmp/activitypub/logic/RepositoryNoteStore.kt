package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.activitypub.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.activitypub.note.NotePosition
import net.matsudamper.activitypub.note.NoteStore
import net.matsudamper.activitypub.note.StoredNote
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * ActivityPub 側の [NoteStore] を DB に繋ぐ。
 * [RepositoryFollowerStore] と同じく型を持ち替えるだけの層になる。
 */
class RepositoryNoteStore(
    private val notes: NoteRepository,
) : NoteStore {
    override fun find(publicId: MastodonPublicNoteId): StoredNote? = notes.find(PublicNoteId(publicId.value))?.toStored()

    override fun findByPublicIds(publicIds: Set<MastodonPublicNoteId>): Map<MastodonPublicNoteId, StoredNote> = notes
        .findByPublicIds(publicIds.map { PublicNoteId(it.value) }.toSet())
        .map { (publicId, note) -> MastodonPublicNoteId(publicId.value) to note.toStored() }
        .toMap()

    override fun delete(publicId: MastodonPublicNoteId) {
        notes.delete(PublicNoteId(publicId.value))
    }

    override fun deleteByUsername(username: String): Int = notes.deleteByUsername(username)

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<StoredNote> = notes
        .list(
            username = username,
            after = after?.toRepository(),
            limit = limit,
        )
        .map { it.toStored() }

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = notes
        .listPositions(
            username = username,
            after = after?.toRepository(),
            limit = limit,
        )
        .map { it.toStored() }

    override fun listAllPositions(
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = notes
        .listAllPositions(
            after = after?.toRepository(),
            limit = limit,
        )
        .map { it.toStored() }

    override fun count(username: String): Long = notes.count(username)

    override fun counts(usernames: Set<String>): Map<String, Long> = notes.counts(usernames)

    private fun NotePosition.toRepository(): net.matsudamper.kmp.activitypub.repository.NotePosition =
        net.matsudamper.kmp.activitypub.repository.NotePosition(
            publishedAt = publishedAt,
            publicId = PublicNoteId(publicId.value),
        )

    private fun net.matsudamper.kmp.activitypub.repository.NotePosition.toStored(): NotePosition = NotePosition(
        publishedAt = publishedAt,
        publicId = MastodonPublicNoteId(publicId.value),
    )

    private fun Note.toStored(): StoredNote = StoredNote(
        publicId = MastodonPublicNoteId(publicId.value),
        username = username,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )
}
