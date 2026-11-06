package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.OutgoingLike
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.activity.OutgoingActivity
import net.matsudamper.kotpub.activitypub.LinkOrObject
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.json.AppJson

/**
 * 他のサーバーの投稿へのいいねを記録して、投稿者への Like / Undo を積む。送るのは配信キューのワーカー
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
     * @param postId タイムラインの投稿の id
     */
    fun setLiked(accountId: AccountId?, postId: String, liked: Boolean): SetLikedResult {
        if (accountId == null) return SetLikedResult.NotLoggedIn
        val account = accountService.account(accountId) ?: return SetLikedResult.NotLoggedIn
        val sender = directory.resolve(account.username) ?: return SetLikedResult.NotLoggedIn
        val target = likes.target(accountId, postId) ?: return SetLikedResult.PostNotFound
        val enqueued = if (liked) {
            val like = likeActivity(
                id = "${sender.actorId}/likes/${UUID.randomUUID()}",
                actorId = sender.actorId,
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
                val like = likeActivity(id = likeId, actorId = sender.actorId, objectUri = target.objectUri)
                val undo = OutgoingActivity(
                    id = "${sender.actorId}/undo/${UUID.randomUUID()}",
                    type = UNDO_TYPE,
                    actor = sender.actorId,
                    target = LinkOrObject.Embedded(AppJson.encodeToJsonElement(OutgoingActivity.serializer(), like) as JsonObject),
                )
                AppJson.encodeToString(OutgoingActivity.serializer(), undo)
            }
        }
        if (enqueued) onEnqueued()
        return SetLikedResult.Updated
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
     * 読めない投稿か、いいねできないこのサーバーの投稿
     */
    data object PostNotFound : SetLikedResult
}
