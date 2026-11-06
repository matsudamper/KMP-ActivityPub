package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.LikeTarget
import net.matsudamper.kmp.activitypub.repository.OutgoingLike
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.activity.OutgoingActivity
import net.matsudamper.kotpub.activitypub.LinkOrObject
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.json.AppJson

/**
 * いいねを記録する。他のサーバーの投稿なら投稿者への Like / Undo も積む。送るのは配信キューのワーカー
 *
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class LikeService(
    private val directory: ActorDirectory,
    private val accountService: AccountService,
    private val likes: LikeRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    /**
     * @param postId タイムラインの投稿の id。他のサーバーの投稿もこのサーバーの投稿も受け付ける
     */
    fun setLiked(accountId: AccountId?, postId: String, liked: Boolean): SetLikedResult {
        if (accountId == null) return SetLikedResult.NotLoggedIn
        val account = accountService.account(accountId) ?: return SetLikedResult.NotLoggedIn
        val sender = directory.resolve(account.username) ?: return SetLikedResult.NotLoggedIn
        val enqueued = when (val target = likes.target(accountId, postId) ?: return SetLikedResult.PostNotFound) {
            // このサーバーの投稿は配らずに記録だけする
            LikeTarget.Local -> {
                if (liked) likes.likeLocal(accountId, postId, clock.instant()) else likes.unlikeLocal(accountId, postId)
                false
            }

            is LikeTarget.Remote -> setRemoteLiked(accountId = accountId, senderActorId = sender.actorId, postId = postId, target = target, liked = liked)
        }
        if (enqueued) onEnqueued()
        return SetLikedResult.Updated
    }

    /**
     * @return 配信を積んだら true
     */
    private fun setRemoteLiked(accountId: AccountId, senderActorId: String, postId: String, target: LikeTarget.Remote, liked: Boolean): Boolean {
        return if (liked) {
            val like = likeActivity(
                id = "$senderActorId/likes/${UUID.randomUUID()}",
                actorId = senderActorId,
                objectUri = target.objectUri,
            )
            likes.likeAndEnqueue(
                OutgoingLike(
                    accountId = accountId,
                    member = postId,
                    likeId = like.id,
                    inbox = target.inbox,
                    body = AppJson.encodeToString(OutgoingActivity.serializer(), like),
                    likedAt = clock.instant(),
                ),
            )
        } else {
            likes.unlikeAndEnqueueUndo(accountId = accountId, member = postId, inbox = target.inbox, now = clock.instant()) { likeId ->
                val like = likeActivity(id = likeId, actorId = senderActorId, objectUri = target.objectUri)
                val undo = OutgoingActivity(
                    id = "$senderActorId/undo/${UUID.randomUUID()}",
                    type = UNDO_TYPE,
                    actor = senderActorId,
                    target = LinkOrObject.Embedded(AppJson.encodeToJsonElement(OutgoingActivity.serializer(), like) as JsonObject),
                )
                AppJson.encodeToString(OutgoingActivity.serializer(), undo)
            }
        }
    }

    private fun likeActivity(id: String, actorId: String, objectUri: String): OutgoingActivity =
        OutgoingActivity(id = id, type = LIKE_TYPE, actor = actorId, target = LinkOrObject.Link(objectUri))

    private companion object {
        const val LIKE_TYPE = "Like"
        const val UNDO_TYPE = "Undo"
    }
}

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
