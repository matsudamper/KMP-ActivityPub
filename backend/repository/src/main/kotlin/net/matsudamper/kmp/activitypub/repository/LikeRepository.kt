package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * こちらのアカウントが他のサーバーの投稿に付けるいいね。
 * 投稿はタイムラインの member（`r` + REMOTE_NOTES の id）で指す
 */
interface LikeRepository {
    /**
     * @return [accountId] が読めない投稿や、他のサーバーの投稿でなければ null
     */
    fun target(accountId: AccountId, member: String): LikeTarget?

    /**
     * いいねを記録して、投稿者への Like を積む。記録と投函を分けると、いいねしたのに相手に届かない状態が残る
     *
     * @return 既にいいねしていれば何も書かずに false
     */
    fun likeAndEnqueue(like: OutgoingLike): Boolean

    /**
     * いいねを消して、投稿者への Undo を積む。まだ一度も送っていない Like は取り消すだけで Undo は積まない
     *
     * @param undoBody 消したいいねの Like の id から Undo のアクティビティを作る
     * @return いいねしていなければ false
     */
    fun unlikeAndEnqueueUndo(
        accountId: AccountId,
        member: String,
        inbox: String,
        now: Instant,
        undoBody: (likeId: String) -> String,
    ): Boolean
}

/**
 * @param objectUri いいねする投稿の id
 * @param inbox 投稿者の inbox
 */
data class LikeTarget(
    val objectUri: String,
    val inbox: String,
)

/**
 * @param likeId 送る Like の id。Undo で指すので記録する
 * @param body 投稿者に送る Like のアクティビティ
 */
data class OutgoingLike(
    val accountId: AccountId,
    val member: String,
    val likeId: String,
    val inbox: String,
    val body: String,
    val likedAt: Instant,
)
