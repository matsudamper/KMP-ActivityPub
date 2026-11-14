package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.repository.LikeReaction
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.LikeTarget
import net.matsudamper.kmp.activitypub.repository.OutgoingLike
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.CustomEmojiId
import net.matsudamper.kotpub.activity.LikeActivities
import net.matsudamper.kotpub.activity.OutgoingActivity
import net.matsudamper.kotpub.activity.ReactionEmoji
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.json.AppJson

/**
 * いいねを記録する。他のサーバーの投稿なら投稿者への Like / Undo も積む。送るのは配信キューのワーカー
 *
 * @param unsavedNotes 保存していない投稿にいいねするときに、取ってきて保存する
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class LikeService(
    private val directory: ActorDirectory,
    private val accountService: AccountService,
    private val likes: LikeRepository,
    private val customEmojis: CustomEmojiService,
    private val customEmojiUrls: CustomEmojiUrls,
    private val unsavedNotes: UnsavedRemoteNotes,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    /**
     * @param requestedPostId タイムラインの投稿の id か、[UnsavedRemoteNoteId] の id。他のサーバーの投稿もこのサーバーの投稿も受け付ける
     * @return 外すときは、いいねしていない投稿でも [SetLikedResult.Updated]
     */
    suspend fun setLiked(accountId: AccountId?, requestedPostId: String, liked: Boolean): SetLikedResult {
        val reaction = if (liked) PostReaction.Like else null
        return when (setReaction(accountId = accountId, requestedPostId = requestedPostId, reaction = reaction)) {
            SetReactionResult.Updated -> SetLikedResult.Updated

            SetReactionResult.NotLoggedIn -> SetLikedResult.NotLoggedIn

            SetReactionResult.PostNotFound -> SetLikedResult.PostNotFound

            // 絵文字の無いいいねでは起きない
            SetReactionResult.InvalidEmoji -> SetLikedResult.PostNotFound
        }
    }

    /**
     * 投稿に付けるいいねかリアクションを、[reaction] に置き換える。1 つの投稿にはどちらか 1 つしか付けない。
     * 他のサーバーにはどちらも Like で送り、相手も 1 つの投稿に 1 つしか持たないため。
     * 違うものが付いていれば、前のものを外してから付ける
     *
     * @param requestedPostId タイムラインの投稿の id か、[UnsavedRemoteNoteId] の id。他のサーバーの投稿もこのサーバーの投稿も受け付ける
     * @param reaction null なら外す。外すときは、付けていない投稿でも [SetReactionResult.Updated]
     */
    suspend fun setReaction(accountId: AccountId?, requestedPostId: String, reaction: PostReaction?): SetReactionResult {
        if (accountId == null) return SetReactionResult.NotLoggedIn
        val account = accountService.account(accountId) ?: return SetReactionResult.NotLoggedIn
        val sender = directory.resolve(account.username) ?: return SetReactionResult.NotLoggedIn
        val sending = when (reaction) {
            null, PostReaction.Like -> null

            is PostReaction.Unicode -> if (isUnicodeEmoji(
                    reaction.emoji,
                )
            ) {
                SendingReaction(ReactionEmoji.Unicode(reaction.emoji), customEmojiId = null, customEmojiPublicId = null)
            } else {
                return SetReactionResult.InvalidEmoji
            }

            is PostReaction.Custom -> {
                val emoji = customEmojis.findByShortcode(reaction.shortcode) ?: return SetReactionResult.InvalidEmoji
                SendingReaction(ReactionEmoji.Custom(customEmojiUrls.emojiObjectOf(emoji)), customEmojiId = emoji.id, customEmojiPublicId = emoji.publicId)
            }
        }
        val reactionEmoji = sending?.emoji
        val likeReaction = sending?.let { LikeReaction(emoji = it.emoji.text, customEmojiId = it.customEmojiId) }
        val postId = unsavedNotes.memberOf(viewer = accountId, postId = requestedPostId, importsMissing = reaction != null)
            ?: return if (reaction != null) SetReactionResult.PostNotFound else SetReactionResult.Updated
        val current = likes.likesOf(accountId, setOf(postId))[postId]
        // 消した絵文字と同じ名前で置き直した絵文字は別のものなので、名前だけでなく画像でも比べる
        val unchanged = current != null && current.reactionEmoji == reactionEmoji?.text && current.customEmojiPublicId == sending?.customEmojiPublicId
        if (reaction != null && unchanged) return SetReactionResult.Updated
        val target = if (reaction == null) null else likes.target(accountId, postId) ?: return SetReactionResult.PostNotFound

        // 外すときは今読めるかを問わない。投稿者のフォローを外した後でも、付けたものを外せるようにする
        val unlikedLocal = current != null && likes.unlikeLocal(accountId, postId)
        val enqueuedUndo = current != null && !unlikedLocal && unlikeRemote(accountId = accountId, senderActorId = sender.actorId, postId = postId)
        val enqueuedLike = when (target) {
            null -> false

            // このサーバーの投稿は配らずに記録だけする
            LikeTarget.Local -> {
                likes.likeLocal(accountId, postId, clock.instant(), likeReaction)
                false
            }

            is LikeTarget.Remote -> likeRemote(accountId = accountId, senderActorId = sender.actorId, postId = postId, target = target, reaction = likeReaction, emoji = reactionEmoji)
        }
        if (enqueuedUndo || enqueuedLike) onEnqueued()
        return SetReactionResult.Updated
    }

    /**
     * @param members タイムラインの member
     * @return [members] のうち [accountId] が絵文字を付けた投稿の、付けた絵文字。絵文字の無いいいねは含めない
     */
    fun viewerReactionsOf(accountId: AccountId, members: Set<String>): Map<String, ViewerReaction> {
        return likes.likesOf(accountId, members).mapNotNull { (member, like) ->
            val emoji = like.reactionEmoji ?: return@mapNotNull null
            member to ViewerReaction(emoji = emoji, imageUrl = like.customEmojiPublicId?.let(customEmojiUrls::imageUrl))
        }.toMap()
    }

    /**
     * @return 配信を積んだら true
     */
    private fun likeRemote(
        accountId: AccountId,
        senderActorId: String,
        postId: String,
        target: LikeTarget.Remote,
        reaction: LikeReaction?,
        emoji: ReactionEmoji?,
    ): Boolean {
        val likeId = "$senderActorId/likes/${UUID.randomUUID()}"
        val like = if (emoji == null) {
            LikeActivities.like(id = likeId, actor = senderActorId, objectUri = target.objectUri)
        } else {
            LikeActivities.reaction(id = likeId, actor = senderActorId, objectUri = target.objectUri, emoji = emoji)
        }
        return likes.likeAndEnqueue(
            OutgoingLike(
                accountId = accountId,
                member = postId,
                likeId = like.id,
                inbox = target.inbox,
                body = AppJson.encodeToString(OutgoingActivity.serializer(), like),
                likedAt = clock.instant(),
                reaction = reaction,
            ),
        )
    }

    /**
     * 送ったときの絵文字を作り直す。カスタム絵文字が消されていたら画像は付けられないので、名前だけを入れる
     */
    private fun reactionEmojiOf(reaction: LikeReaction): ReactionEmoji {
        val customEmoji = reaction.customEmojiId?.let(customEmojis::findById)
        return if (customEmoji == null) ReactionEmoji.Unicode(reaction.emoji) else ReactionEmoji.Custom(customEmojiUrls.emojiObjectOf(customEmoji))
    }

    /**
     * @return 外したら true
     */
    private fun unlikeRemote(accountId: AccountId, senderActorId: String, postId: String): Boolean {
        return likes.unlikeAndEnqueueUndo(accountId = accountId, member = postId, now = clock.instant()) { likeId, objectUri, reaction ->
            val emoji = reaction?.let(::reactionEmojiOf)
            val like = if (emoji == null) {
                LikeActivities.like(id = likeId, actor = senderActorId, objectUri = objectUri)
            } else {
                LikeActivities.reaction(id = likeId, actor = senderActorId, objectUri = objectUri, emoji = emoji)
            }
            val undo = LikeActivities.undo(id = "$senderActorId/undo/${UUID.randomUUID()}", like = like)
            AppJson.encodeToString(OutgoingActivity.serializer(), undo)
        }
    }
}

/**
 * @param imageUrl このサーバーのカスタム絵文字の画像の URL。Unicode の絵文字か、消された絵文字なら null
 */
data class ViewerReaction(val emoji: String, val imageUrl: String?)

/**
 * 1 つの投稿に付けるもの
 */
sealed interface PostReaction {
    /** 絵文字の無いいいね */
    data object Like : PostReaction

    data class Unicode(val emoji: String) : PostReaction

    /**
     * @param shortcode このサーバーのカスタム絵文字の、コロンを含めない名前
     */
    data class Custom(val shortcode: String) : PostReaction
}

sealed interface SetReactionResult {
    /**
     * 既に望んだ状態だったときも含む
     */
    data object Updated : SetReactionResult

    data object NotLoggedIn : SetReactionResult

    /**
     * 無いか、読めない投稿
     */
    data object PostNotFound : SetReactionResult

    /**
     * 絵文字 1 つではないか、無いカスタム絵文字
     */
    data object InvalidEmoji : SetReactionResult
}

/**
 * 他のサーバーは本文の文字をそのまま出すので、コロンで囲んだ名前や空白、英字の入った文字を絵文字として送らない。
 * ℹ️ のように文字に分類される絵文字もあるので、文字の分類では弾かない
 */
private fun isUnicodeEmoji(text: String): Boolean =
    text.isNotEmpty() &&
        text.codePointCount(0, text.length) <= MAX_UNICODE_EMOJI_CODE_POINTS &&
        text.none { it.isWhitespace() || it == ':' || it in 'a'..'z' || it in 'A'..'Z' }

/**
 * @param customEmojiId このサーバーのカスタム絵文字なら、その id
 * @param customEmojiPublicId このサーバーのカスタム絵文字なら、画像の公開 id
 */
private class SendingReaction(val emoji: ReactionEmoji, val customEmojiId: CustomEmojiId?, val customEmojiPublicId: String?)

// 肌の色や家族の絵文字は、いくつものコードポイントを繋いで 1 つになる
private const val MAX_UNICODE_EMOJI_CODE_POINTS = 16

sealed interface SetLikedResult {
    /**
     * 既に望んだ状態だったときも含む
     */
    data object Updated : SetLikedResult

    data object NotLoggedIn : SetLikedResult

    /**
     * 無いか、読めない投稿
     */
    data object PostNotFound : SetLikedResult
}
