package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.LikeTarget
import net.matsudamper.kmp.activitypub.repository.OutgoingLike
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.activity.LikeActivities
import net.matsudamper.kotpub.activity.OutgoingActivity
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
    private val unsavedNotes: UnsavedRemoteNotes,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    /**
     * @param requestedPostId タイムラインの投稿の id か、[UnsavedRemoteNoteId] の id。他のサーバーの投稿もこのサーバーの投稿も受け付ける
     * @return 外すときは、いいねしていない投稿でも [SetLikedResult.Updated]
     */
    suspend fun setLiked(accountId: AccountId?, requestedPostId: String, liked: Boolean): SetLikedResult {
        if (accountId == null) return SetLikedResult.NotLoggedIn
        val account = accountService.account(accountId) ?: return SetLikedResult.NotLoggedIn
        val sender = directory.resolve(account.username) ?: return SetLikedResult.NotLoggedIn
        val postId = unsavedNotes.memberOf(viewer = accountId, postId = requestedPostId, importsMissing = liked)
            ?: return if (liked) SetLikedResult.PostNotFound else SetLikedResult.Updated
        val enqueued = if (liked) {
            when (val target = likes.target(accountId, postId) ?: return SetLikedResult.PostNotFound) {
                // このサーバーの投稿は配らずに記録だけする
                LikeTarget.Local -> {
                    likes.likeLocal(accountId, postId, clock.instant())
                    false
                }

                is LikeTarget.Remote -> likeRemote(accountId = accountId, senderActorId = sender.actorId, postId = postId, target = target)
            }
        } else {
            // 外すときは今読めるかを問わない。投稿者のフォローを外した後でも、付けたいいねを外せるようにする
            val unlikedLocal = likes.unlikeLocal(accountId, postId)
            !unlikedLocal && unlikeRemote(accountId = accountId, senderActorId = sender.actorId, postId = postId)
        }
        if (enqueued) onEnqueued()
        return SetLikedResult.Updated
    }

    /**
     * @return 配信を積んだら true
     */
    private fun likeRemote(accountId: AccountId, senderActorId: String, postId: String, target: LikeTarget.Remote): Boolean {
        val like = LikeActivities.like(
            id = "$senderActorId/likes/${UUID.randomUUID()}",
            actor = senderActorId,
            objectUri = target.objectUri,
        )
        return likes.likeAndEnqueue(
            OutgoingLike(
                accountId = accountId,
                member = postId,
                likeId = like.id,
                inbox = target.inbox,
                body = AppJson.encodeToString(OutgoingActivity.serializer(), like),
                likedAt = clock.instant(),
            ),
        )
    }

    /**
     * @return 外したら true
     */
    private fun unlikeRemote(accountId: AccountId, senderActorId: String, postId: String): Boolean {
        return likes.unlikeAndEnqueueUndo(accountId = accountId, member = postId, now = clock.instant()) { likeId, objectUri ->
            val like = LikeActivities.like(id = likeId, actor = senderActorId, objectUri = objectUri)
            val undo = LikeActivities.undo(id = "$senderActorId/undo/${UUID.randomUUID()}", like = like)
            AppJson.encodeToString(OutgoingActivity.serializer(), undo)
        }
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
