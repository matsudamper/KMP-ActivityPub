package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 投稿に添える前の画像。添えるのは [DeliveryQueueRepository.enqueueNote] で、投稿の記録と同じトランザクションにする
 */
interface NoteImageRepository {
    /**
     * @return アカウントが無ければ false
     */
    fun add(image: NewNoteImage): Boolean

    /**
     * まだ投稿に添えていない、[accountId] の画像を [ids] の順に返す。見つからない ID は落とす
     */
    fun findUnattached(
        accountId: AccountId,
        ids: List<NoteImageId>,
    ): List<NoteImage>
}

@JvmInline
value class NoteImageId(val value: String)

/**
 * @param path 画像の置き場の中での場所
 */
data class NewNoteImage(
    val accountId: AccountId,
    val id: NoteImageId,
    val path: String,
    val mediaType: String,
    val description: String?,
    val width: Int,
    val height: Int,
    val uploadedAt: Instant,
)

/**
 * @param path 画像の置き場の中での場所
 */
data class NoteImage(
    val id: NoteImageId,
    val path: String,
    val mediaType: String,
    val description: String?,
    val width: Int,
    val height: Int,
)
