package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 他のサーバーから届いたブースト。こちらの投稿のブーストと、タイムラインに出す他のサーバーの投稿のブーストの両方を扱う
 */
interface BoostRepository {
    /**
     * こちらの投稿のブースト。押した相手のアクターも記録する。相手が消えた後の Delete を検証するのに公開鍵が要る
     *
     * @return 記録したら true。記録済みか投稿が無ければ false
     */
    fun add(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        activityUri: String,
        receivedAt: Instant,
    ): Boolean

    /**
     * こちらの投稿のブーストも、タイムラインに出したブーストも消す
     *
     * @return 消したら true
     */
    fun remove(
        actorUri: String,
        activityUri: String,
    ): Boolean

    /**
     * @return 消えたブーストの数
     */
    fun removeActor(actorUri: String): Int

    fun findPublicKeyPem(actorUri: String): String?

    /**
     * こちらの投稿のブーストの数。他のサーバーから届いたものと、このサーバーのアカウントのものを合わせる。1 件も無い投稿も 0 で返す
     */
    fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Long>
}
