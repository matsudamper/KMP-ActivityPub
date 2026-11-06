package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 他のサーバーから届いた、こちらの投稿へのお気に入り
 */
interface FavouriteRepository {
    /**
     * 押した相手のアクターも記録する。相手が消えた後の Delete を検証するのに公開鍵が要る
     *
     * @return 記録したら true。記録済みか投稿が無ければ false
     */
    fun add(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        receivedAt: Instant,
    ): Boolean

    fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean

    /**
     * @return 消えたお気に入りの数
     */
    fun removeActor(actorUri: String): Int

    fun findPublicKeyPem(actorUri: String): String?

    /**
     * このサーバーのアカウントのいいねも合わせて数える。1 件も無い投稿も 0 で返す
     */
    fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Long>

    fun rememberEarlyUndone(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    )

    fun isEarlyUndone(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean
}
