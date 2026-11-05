package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 他のサーバーから届いた、こちらの投稿への絵文字のスタンプ
 */
interface StampRepository {
    /**
     * 押した相手のアクターも記録する。相手が消えた後の Delete を検証するのに公開鍵が要る。
     * 同じ相手が同じ絵文字を押し直したら、カスタム絵文字の画像を置き換える
     *
     * @return 投稿が無ければ false
     */
    fun put(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        emoji: String,
        emojiImageUrl: String?,
        receivedAt: Instant,
    ): Boolean

    fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean

    /**
     * @return 消えたスタンプの数
     */
    fun removeActor(actorUri: String): Int

    fun findPublicKeyPem(actorUri: String): String?

    /**
     * 投稿ごとに、多く押された絵文字から並べる。1 件も無い投稿は空で返す
     */
    fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<StampCount>>
}

/**
 * 同じ :name: でもサーバーごとに画像が違うので、画像の URL まで同じものをまとめる
 *
 * @param emojiImageUrl カスタム絵文字の画像の URL。Unicode の絵文字では null
 */
data class StampCount(
    val emoji: String,
    val emojiImageUrl: String?,
    val count: Long,
)
