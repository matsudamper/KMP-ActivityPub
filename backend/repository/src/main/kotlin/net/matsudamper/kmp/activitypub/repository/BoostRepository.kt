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
     * こちらの投稿のブーストは消す。タイムラインに出したブーストは、取り消し済みと出すので印を付けて残す
     *
     * @return 取り消したブースト。該当するブーストが無いか、既に取り消されていれば null
     */
    fun remove(
        actorUri: String,
        activityUri: String,
        undoneAt: Instant,
    ): BoostRemoval?

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

/**
 * @param undoneTimelineMember タイムラインに出していたブーストなら、その member。こちらの投稿のブーストだけなら null
 */
data class BoostRemoval(val undoneTimelineMember: String?)
