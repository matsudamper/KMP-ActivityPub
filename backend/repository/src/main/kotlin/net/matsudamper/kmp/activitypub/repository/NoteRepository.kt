package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 配った投稿。記録は配信の投函と同時に行うので [DeliveryQueueRepository.enqueueNote] にある
 */
interface NoteRepository {
    fun find(publicId: PublicNoteId): Note?

    fun findByPublicIds(publicIds: Set<PublicNoteId>): Map<PublicNoteId, Note>

    fun findImage(publicId: NoteImagePublicId): StoredNoteImage?

    fun addUploadedImage(upload: UploadedNoteImage)

    /**
     * [ownerAccountId] が置いた、期限内の画像を渡した順に返す。1 枚でも無ければ null
     */
    fun findUploadedImages(
        ownerAccountId: AccountId,
        publicIds: List<NoteImagePublicId>,
        now: Instant,
    ): List<NoteImage>?

    /**
     * 期限を過ぎてもまだ投稿に付いていない画像を、期限の古い順に返す
     */
    fun listExpiredUploadedImages(
        now: Instant,
        limit: Int,
    ): List<UploadedNoteImage>

    /**
     * 期限を過ぎてまだ投稿に付いていなければ記録を消す。間に投稿に付いたら消さない
     *
     * @return 消したら true。置き場の画像はこれが true のときだけ消す
     */
    fun deleteExpiredUploadedImage(
        publicId: NoteImagePublicId,
        now: Instant,
    ): Boolean

    /**
     * [DeliveryQueueRepository.enqueueNote] に渡した requestId で記録した投稿。無ければ null
     */
    fun findPublicIdByRequestId(
        username: String,
        requestId: String,
    ): PublicNoteId?

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

/**
 * @param images 付けた順
 */
data class Note(
    val publicId: PublicNoteId,
    val username: String,
    val contentHtml: String,
    val publishedAt: Instant,
    val images: List<NoteImage>,
)

/**
 * @param path 画像の置き場の中での場所
 * @param description 画像の説明文。無ければ null
 */
data class NoteImage(
    val publicId: NoteImagePublicId,
    val path: String,
    val contentType: String,
    val description: String?,
)

/**
 * @param ownerAccountId 画像の置き場はアカウントごとに権限を分けているので、読むときに要る
 */
data class StoredNoteImage(
    val ownerAccountId: AccountId,
    val image: NoteImage,
)

/**
 * @param expiresAt これを過ぎても投稿に付いていなければ消す
 */
data class UploadedNoteImage(
    val ownerAccountId: AccountId,
    val image: NoteImage,
    val expiresAt: Instant,
)

/**
 * 画像の URL に入る識別子。投稿の公開 id は配る文書を組み立てるときに決まるので、画像には別の識別子を付ける
 */
@JvmInline
value class NoteImagePublicId(val value: String)
