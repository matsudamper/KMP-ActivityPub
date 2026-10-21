package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 投函した投稿を投稿として引けるよう、[FakeDeliveryQueueRepository] と同じ置き場を持つ
 */
internal class FakeNoteRepository : NoteRepository {
    val notes = mutableListOf<Note>()

    override fun find(publicId: PublicNoteId): Note? = notes.firstOrNull { it.publicId == publicId }

    override fun findByPublicIds(publicIds: Set<PublicNoteId>): Map<PublicNoteId, Note> =
        notes.filter { it.publicId in publicIds }.associateBy { it.publicId }

    override fun delete(publicId: PublicNoteId) {
        notes.removeAll { it.publicId == publicId }
    }

    override fun deleteByUsername(username: String): Int {
        val before = notes.size
        notes.removeAll { it.username.equals(username, ignoreCase = true) }
        return before - notes.size
    }

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<Note> = sorted(username = username, after = after).take(limit)

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = list(username = username, after = after, limit = limit).map { it.position() }

    override fun listAllPositions(
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = sorted(username = null, after = after).take(limit).map { it.position() }

    override fun count(username: String): Long = notes.count { it.username.equals(username, ignoreCase = true) }.toLong()

    override fun counts(usernames: Set<String>): Map<String, Long> = usernames.associateWith { count(it) }

    private fun sorted(
        username: String?,
        after: NotePosition?,
    ): List<Note> =
        notes
            .filter { username == null || it.username.equals(username, ignoreCase = true) }
            .sortedWith(compareByDescending<Note> { it.publishedAt }.thenByDescending { it.publicId.value })
            .dropWhile { after != null && (it.publishedAt > after.publishedAt || (it.publishedAt == after.publishedAt && it.publicId.value >= after.publicId.value)) }

    private fun Note.position(): NotePosition = NotePosition(publishedAt = publishedAt, publicId = publicId)
}
