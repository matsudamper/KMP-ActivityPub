package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * こちらのアカウントが付けるブースト。他のサーバーの公開・公開タイムラインに載せない投稿と、このサーバーの投稿を扱う。
 * 投稿はタイムラインの member（`r` + REMOTE_NOTES の id、`l` + NOTES の id、ブーストで載った投稿は `b` + REMOTE_BOOSTS の id、自分のブーストは `a` + ACCOUNT_BOOSTS の id）で指す。
 * ブーストで載った投稿のブーストは、ブーストされた元の投稿に付ける
 */
interface AccountBoostRepository {
    /**
     * @return 無い投稿、フォロワー限定などブーストできない投稿なら null。公開している投稿は誰でも読めるので、フォローしていなくてもブーストできる
     */
    fun target(accountId: AccountId, member: String): BoostTarget?

    /**
     * ブーストを記録して、[OutgoingBoost.inboxes] への Announce を積む。記録と投函を分けると、ブーストしたのに相手に届かない状態が残る
     *
     * @return 自分のタイムラインに載せるブーストの位置。既にブーストしていれば何も書かずに null
     */
    fun boostAndEnqueue(boost: OutgoingBoost): TimelinePosition?

    /**
     * ブーストを消して、Announce を積んだ宛先への Undo を積む。まだ一度も送っていない Announce の宛先には、取り消すだけで Undo は積まない。
     * 今読めるかは問わない。投稿者のフォローを外した後でも、付けたブーストを外せるようにする
     *
     * @return ブーストしていなければ false
     */
    fun unboostAndEnqueueUndo(accountId: AccountId, member: String, now: Instant): Boolean

    /**
     * 投稿者が消した他のサーバーの投稿を消す。その投稿へのこのサーバーのアカウントのブーストもすべて外し、[unboostAndEnqueueUndo] と同じく Undo を積む。
     * 投稿者の Delete がこちらのフォロワーのサーバーに届くとは限らないので、ブーストを届けた宛先にはこちらから取り消しを送る。
     * 投稿を消すとブーストの記録も消えるので、ブーストの取り消しと投稿の削除は分けずに行う。投稿が [actorUri] のものでなければ何もしない
     *
     * @return 外したブーストの数
     */
    fun deleteRemoteNoteAndUndoBoosts(actorUri: String, objectUri: String, now: Instant): Int

    /**
     * outbox に出すブースト。新しい順で、同じ時刻なら Announce の id の大きい順
     *
     * @param after ここより古いものを返す。null なら先頭から
     */
    fun listForOutbox(username: String, after: AccountBoostPosition?, limit: Int): List<AccountBoostRecord>

    fun countForOutbox(username: String): Long

    /**
     * @return [publicIds] のうち、[accountId] がブーストしているこのサーバーの投稿
     */
    fun boostedLocalNotes(accountId: AccountId, publicIds: Set<PublicNoteId>): Set<PublicNoteId>
}

sealed interface BoostTarget {
    /**
     * @param objectUri ブーストする投稿の id
     * @param authorActorUri 投稿者のアクター id
     * @param authorInbox 投稿者の inbox
     * @param unlisted 公開タイムラインに載せない投稿か。ブーストも同じく載せないように配る
     */
    data class Remote(
        val objectUri: String,
        val authorActorUri: String,
        val authorInbox: String,
        val unlisted: Boolean,
    ) : BoostTarget

    /**
     * @param authorUsername 投稿したこのサーバーのアカウント
     */
    data class Local(
        val publicId: PublicNoteId,
        val authorUsername: String,
    ) : BoostTarget
}

/**
 * @param announceId 送る Announce の id
 * @param announceBody 送る Announce のアクティビティ
 * @param undoBody 外すときに送る Undo のアクティビティ。投稿が消えたときにも送るので、ブーストするときに作っておく
 * @param inboxes Announce を送る inbox
 * @param objectUri ブーストする投稿の id
 * @param objectAuthor ブーストする投稿の投稿者のアクター id
 * @param unlisted 公開タイムラインに載せない形で配ったか
 */
data class OutgoingBoost(
    val accountId: AccountId,
    val member: String,
    val announceId: String,
    val announceBody: String,
    val undoBody: String,
    val inboxes: List<String>,
    val objectUri: String,
    val objectAuthor: String,
    val unlisted: Boolean,
    val boostedAt: Instant,
)

/**
 * outbox の続きを指す位置
 */
data class AccountBoostPosition(
    val boostedAt: Instant,
    val announceId: String,
)

/**
 * outbox に出すブースト 1 件
 */
data class AccountBoostRecord(
    val announceId: String,
    val objectUri: String,
    val objectAuthor: String,
    val unlisted: Boolean,
    val boostedAt: Instant,
)
