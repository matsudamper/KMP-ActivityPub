package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePosition as RepositoryNotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NotePosition
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.note.StoredNote

internal class RepositoryNoteStore(
    private val noteRepository: NoteRepository,
) : NoteStore {
    override fun find(publicId: KotpubPublicNoteId): StoredNote? = noteRepository.find(publicId.toRepository())?.toStored()

    override fun findByPublicIds(publicIds: Set<KotpubPublicNoteId>): Map<KotpubPublicNoteId, StoredNote> =
        noteRepository
            .findByPublicIds(publicIds.map { it.toRepository() }.toSet())
            .map { (publicId, note) -> KotpubPublicNoteId(publicId.value) to note.toStored() }
            .toMap()

    override fun delete(publicId: KotpubPublicNoteId) {
        noteRepository.delete(publicId.toRepository())
    }

    override fun deleteByUsername(username: String): Int = noteRepository.deleteByUsername(username)

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<StoredNote> =
        noteRepository
            .list(username = username, after = after?.toRepository(), limit = limit)
            .map { it.toStored() }

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> =
        noteRepository
            .listPositions(username = username, after = after?.toRepository(), limit = limit)
            .map { it.toStored() }

    override fun listAllPositions(
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> =
        noteRepository
            .listAllPositions(after = after?.toRepository(), limit = limit)
            .map { it.toStored() }

    override fun count(username: String): Long = noteRepository.count(username)

    override fun counts(usernames: Set<String>): Map<String, Long> = noteRepository.counts(usernames)

    private fun KotpubPublicNoteId.toRepository(): PublicNoteId = PublicNoteId(value)

    private fun NotePosition.toRepository(): RepositoryNotePosition =
        RepositoryNotePosition(publishedAt = publishedAt, publicId = publicId.toRepository())

    private fun RepositoryNotePosition.toStored(): NotePosition =
        NotePosition(publishedAt = publishedAt, publicId = KotpubPublicNoteId(publicId.value))

    private fun Note.toStored(): StoredNote =
        StoredNote(
            publicId = KotpubPublicNoteId(publicId.value),
            username = username,
            contentHtml = contentHtml,
            publishedAt = publishedAt,
        )
}
