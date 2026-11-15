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
     * 投稿ごとに、他のサーバーから届いたスタンプと、このサーバーのアカウントが絵文字を付けたいいねを合わせて、先に押された絵文字から並べる。
     * 1 件も無い投稿は空で返す
     */
    fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<StampCount>>
}

/**
 * 同じ :name: でもサーバーごとに画像が違うので、画像の URL まで同じものをまとめる
 *
 * @param emojiImageUrl 他のサーバーのカスタム絵文字の画像の URL。Unicode の絵文字と、このサーバーのカスタム絵文字では null
 * @param localCustomEmojiPublicId このサーバーのカスタム絵文字なら、画像の公開 id。消された絵文字と、それ以外では null
 */
data class StampCount(
    val emoji: String,
    val emojiImageUrl: String?,
    val count: Long,
    val localCustomEmojiPublicId: String?,
)
