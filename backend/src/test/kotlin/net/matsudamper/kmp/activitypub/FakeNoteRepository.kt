package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImagePublicId
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.StoredNoteImage
import net.matsudamper.kmp.activitypub.repository.UploadedNoteImage
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 投函した投稿を投稿として引けるよう、[FakeDeliveryQueueRepository] と同じ置き場を持つ
 */
internal class FakeNoteRepository : NoteRepository {
    val notes = mutableListOf<Note>()

    override fun find(publicId: PublicNoteId): Note? = notes.firstOrNull { it.publicId == publicId }

    override fun findByPublicIds(publicIds: Set<PublicNoteId>): Map<PublicNoteId, Note> =
        notes.filter { it.publicId in publicIds }.associateBy { it.publicId }

    /**
     * 画像の持ち主は区別しないので、どれもアカウント 1 のものとして返す
     */
    override fun findImage(publicId: NoteImagePublicId): StoredNoteImage? =
        notes.flatMap { it.images }.firstOrNull { it.publicId == publicId }?.let { StoredNoteImage(ownerAccountId = AccountId(1), image = it) }

    val requestIds = mutableMapOf<Pair<String, String>, PublicNoteId>()
    val uploadedImages = mutableListOf<UploadedNoteImage>()

    override fun addUploadedImage(upload: UploadedNoteImage) {
        uploadedImages += upload
    }

    override fun findUploadedImages(
        ownerAccountId: AccountId,
        publicIds: List<NoteImagePublicId>,
        now: Instant,
    ): List<NoteImage>? =
        publicIds.map { publicId ->
            uploadedImages.firstOrNull { it.image.publicId == publicId && it.ownerAccountId == ownerAccountId && it.expiresAt > now }?.image
                ?: return null
        }

    override fun listExpiredUploadedImages(
        now: Instant,
        limit: Int,
    ): List<UploadedNoteImage> = uploadedImages.filter { it.expiresAt <= now }.sortedBy { it.expiresAt }.take(limit)

    override fun deleteExpiredUploadedImage(
        publicId: NoteImagePublicId,
        now: Instant,
    ): Boolean = uploadedImages.removeAll { it.image.publicId == publicId && it.expiresAt <= now }

    override fun findPublicIdByRequestId(
        username: String,
        requestId: String,
    ): PublicNoteId? = requestIds[username.lowercase() to requestId]

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
