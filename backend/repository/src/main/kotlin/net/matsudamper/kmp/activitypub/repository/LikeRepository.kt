package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.CustomEmojiId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * こちらのアカウントが付けるいいね。絵文字を付けたいいねがリアクションで、1 つの投稿に付けられるのはいいねかリアクションのどちらか 1 つ。
 * 他のサーバーにはどちらも Like で送り、相手も 1 つの投稿に 1 つしか持たないため。
 * 投稿はタイムラインの member（他のサーバーの投稿は `r` + REMOTE_NOTES の id、このサーバーの投稿は `l` + NOTES の id、
 * ブーストで載った投稿は `b` + REMOTE_BOOSTS の id）で指す。ブーストで載った投稿へのいいねは、ブーストされた元の投稿に付ける
 */
interface LikeRepository {
    /**
     * @return [accountId] が読めない投稿や、無い投稿なら null。公開している投稿は誰でも読めるので、フォローしていなくてもいいねできる
     */
    fun target(accountId: AccountId, member: String): LikeTarget?

    /**
     * このサーバーの投稿へのいいね。配らないので記録だけする
     *
     * @param reaction 付ける絵文字。null なら絵文字の無いいいね
     * @return 既にいいねしているか、このサーバーの投稿でなければ false
     */
    fun likeLocal(accountId: AccountId, member: String, likedAt: Instant, reaction: LikeReaction?): Boolean

    /**
     * @return いいねしていないか、このサーバーの投稿でなければ false
     */
    fun unlikeLocal(accountId: AccountId, member: String): Boolean

    /**
     * @return [notePublicIds] のうち [accountId] がいいねしている投稿
     */
    fun likedLocalNotes(accountId: AccountId, notePublicIds: Set<PublicNoteId>): Set<PublicNoteId>

    /**
     * @param members タイムラインの member。ブーストで載った投稿は、ブーストされた元の投稿へのいいねを返す
     * @return [members] のうち [accountId] がいいねしている投稿の、付けた絵文字
     */
    fun likesOf(accountId: AccountId, members: Set<String>): Map<String, AccountLike>

    /**
     * [username] がいいねした投稿のうち、誰でも読める公開の投稿を、いいねした新しい順に返す。Actor の liked に載せる
     *
     * @param before この位置より古いいいねを返す。null なら最新から
     */
    fun publishedLikes(username: String, before: LikedNotePosition?, limit: Int): List<PublishedLike>

    /**
     * [publishedLikes] で返る件数
     */
    fun countPublishedLikes(username: String): Long

    /**
     * いいねを記録して、投稿者への Like を積む。記録と投函を分けると、いいねしたのに相手に届かない状態が残る
     *
     * @return 既にいいねしていれば何も書かずに false
     */
    fun likeAndEnqueue(like: OutgoingLike): Boolean

    /**
     * いいねを消して、投稿者への Undo を積む。まだ一度も送っていない Like は取り消すだけで Undo は積まない。
     * 今読めるかは問わない。投稿者のフォローを外した後でも、付けたいいねを外せるようにする
     *
     * @param undoBody 消したいいねの Like の id と、いいねした投稿の id と、付けていた絵文字から Undo のアクティビティを作る。
     *   受け取る側には Like の id を覚えず絵文字で取り消すものもあるので、送ったときと同じ絵文字を入れる
     * @return いいねしていなければ false
     */
    fun unlikeAndEnqueueUndo(
        accountId: AccountId,
        member: String,
        now: Instant,
        undoBody: (likeId: String, objectUri: String, reaction: LikeReaction?) -> String,
    ): Boolean
}

/**
 * @param reactionEmoji 付けた絵文字。null なら絵文字の無いいいね
 * @param customEmojiPublicId 付けたカスタム絵文字の画像の公開 id。Unicode の絵文字と、消された絵文字では null
 */
data class AccountLike(val reactionEmoji: String?, val customEmojiPublicId: String?)

/**
 * いいねに付ける絵文字
 *
 * @param emoji Unicode の絵文字そのものか、カスタム絵文字の `:shortcode:`
 * @param customEmojiId カスタム絵文字なら、その id。消した後に同じ名前で別の画像を置けるので、名前ではなく id で結び付ける
 */
data class LikeReaction(val emoji: String, val customEmojiId: CustomEmojiId?)

/**
 * いいねの一覧の続きを指す位置
 *
 * @param member いいねした投稿の member。同じ時刻のいいねを並べる
 */
data class LikedNotePosition(val likedAt: Instant, val member: String)

/**
 * @param noteRef いいねした投稿。URL はこのサーバーのドメインで決まるので、ここでは作らない
 */
data class PublishedLike(
    val noteRef: TimelineNoteRef,
    val position: LikedNotePosition,
)

sealed interface LikeTarget {
    /**
     * @param objectUri いいねする投稿の id
     * @param inbox 投稿者の inbox
     */
    data class Remote(
        val objectUri: String,
        val inbox: String,
    ) : LikeTarget

    data class Local(val publicId: PublicNoteId) : LikeTarget
}

/**
 * @param likeId 送る Like の id。Undo で指すので記録する
 * @param body 投稿者に送る Like のアクティビティ
 * @param reaction 付ける絵文字。null なら絵文字の無いいいね
 */
data class OutgoingLike(
    val accountId: AccountId,
    val member: String,
    val likeId: String,
    val inbox: String,
    val body: String,
    val likedAt: Instant,
    val reaction: LikeReaction?,
)
