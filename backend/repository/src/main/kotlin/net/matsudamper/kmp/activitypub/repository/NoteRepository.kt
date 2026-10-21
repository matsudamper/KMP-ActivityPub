package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 配った投稿。記録は配信の投函と同時に行うので [DeliveryQueueRepository.enqueueNote] にある
 */
interface NoteRepository {
    fun find(publicId: PublicNoteId): Note?

    fun findByPublicIds(publicIds: Set<PublicNoteId>): Map<PublicNoteId, Note>

    /**
     * まだ送っていない配信も一緒に消える
     */
    fun delete(publicId: PublicNoteId)

    /**
     * @return 消えた件数
     */
    fun deleteByUsername(username: String): Int

    /**
     * 新しい順に返す
     *
     * @param after これより古いものを返す。null なら先頭から
     */
    fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<Note>

    fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition>

    /**
     * アカウントを問わず新しい順に返す
     */
    fun listAllPositions(
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition>

    fun count(username: String): Long

    fun counts(usernames: Set<String>): Map<String, Long>
}

/**
 * 同じ時刻の投稿があってもページの境目が決まるよう、公開 id まで持つ
 */
data class NotePosition(
    val publishedAt: Instant,
    val publicId: PublicNoteId,
)

data class Note(
    val publicId: PublicNoteId,
    val username: String,
    val contentHtml: String,
    val publishedAt: Instant,
)
