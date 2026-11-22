package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * 投稿者が消した他のサーバーの投稿を管理画面で見て、保存から消す。
 * 消された投稿はタイムラインに削除済みと出すため、Delete を受け取っても行を残している
 */
interface DeletedRemoteNoteRepository {
    /**
     * 受け取った順の新しい順
     *
     * @param beforeId この id より前に受け取った分を返す。最初のページなら null
     */
    fun page(
        beforeId: Long?,
        limit: Int,
    ): List<DeletedRemoteNote>

    /**
     * 消された投稿を、付いていたいいね・ブースト・通知ごと保存から消す。タイムラインにも出なくなる
     *
     * @return 消された投稿として保存していなければ false
     */
    fun purge(id: Long): Boolean

    /**
     * 消された投稿を全て [purge] する
     *
     * @return 消した数
     */
    fun purgeAll(): Int
}

/**
 * @param actorName 投稿者の preferredUsername。届いていなければ null
 * @param contentHtml 消される前の本文
 */
data class DeletedRemoteNote(
    val id: Long,
    val objectUri: String,
    val actorUri: String,
    val actorName: String?,
    val contentHtml: String,
    val publishedAt: Instant,
    val deletedAt: Instant,
)
