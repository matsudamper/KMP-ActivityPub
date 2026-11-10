package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * こちらのアカウントが付けるブースト。今は他のサーバーの公開・未収載の投稿だけを扱う。
 * 投稿はタイムラインの member（`r` + REMOTE_NOTES の id、ブーストで載った投稿は `b` + REMOTE_BOOSTS の id）で指す。
 * ブーストで載った投稿のブーストは、ブーストされた元の投稿に付ける
 */
interface AccountBoostRepository {
    /**
     * @return [accountId] が読めない投稿、無い投稿、公開・未収載でない投稿、このサーバーの投稿なら null
     */
    fun target(accountId: AccountId, member: String): BoostTarget?

    /**
     * ブーストを記録して、[OutgoingBoost.inboxes] への Announce を積む。記録と投函を分けると、ブーストしたのに相手に届かない状態が残る
     *
     * @return 既にブーストしていれば何も書かずに false
     */
    fun boostAndEnqueue(boost: OutgoingBoost): Boolean

    /**
     * ブーストを消して、Announce を積んだ宛先への Undo を積む。まだ一度も送っていない Announce の宛先には、取り消すだけで Undo は積まない
     *
     * @param undoBody 消したブーストの Announce のアクティビティから Undo のアクティビティを作る
     * @return ブーストしていなければ false
     */
    fun unboostAndEnqueueUndo(
        accountId: AccountId,
        member: String,
        now: Instant,
        undoBody: (announceBody: String) -> String,
    ): Boolean
}

/**
 * @param objectUri ブーストする投稿の id
 * @param authorActorUri 投稿者のアクター id
 * @param authorInbox 投稿者の inbox
 */
data class BoostTarget(
    val objectUri: String,
    val authorActorUri: String,
    val authorInbox: String,
)

/**
 * @param announceId 送る Announce の id
 * @param body 送る Announce のアクティビティ。Undo に埋めるので記録する
 * @param inboxes Announce を送る inbox
 */
data class OutgoingBoost(
    val accountId: AccountId,
    val member: String,
    val announceId: String,
    val inboxes: List<String>,
    val body: String,
    val boostedAt: Instant,
)
