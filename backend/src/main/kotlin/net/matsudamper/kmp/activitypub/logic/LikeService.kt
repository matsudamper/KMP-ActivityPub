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
 * @param remoteNotes 保存していない投稿をいいねするときに、取ってきて保存する
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class LikeService(
    private val directory: ActorDirectory,
    private val accountService: AccountService,
    private val likes: LikeRepository,
    private val remoteNotes: RemoteNoteImporter,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    /**
     * @param postId タイムラインの投稿の id か、[UnsavedRemoteNoteLikeId] で作った保存していない投稿の id。
     *   他のサーバーの投稿もこのサーバーの投稿も受け付ける
     */
    suspend fun setLiked(accountId: AccountId?, postId: String, liked: Boolean): SetLikedResult {
        if (accountId == null) return SetLikedResult.NotLoggedIn
        val account = accountService.account(accountId) ?: return SetLikedResult.NotLoggedIn
        val sender = directory.resolve(account.username) ?: return SetLikedResult.NotLoggedIn
        val member = memberOf(postId) ?: return SetLikedResult.PostNotFound
        val enqueued = when (val target = likes.target(accountId, member) ?: return SetLikedResult.PostNotFound) {
            // このサーバーの投稿は配らずに記録だけする
            LikeTarget.Local -> {
                if (liked) likes.likeLocal(accountId, member, clock.instant()) else likes.unlikeLocal(accountId, member)
                false
            }

            is LikeTarget.Remote -> setRemoteLiked(accountId = accountId, senderActorId = sender.actorId, postId = member, target = target, liked = liked)
        }
        if (enqueued) onEnqueued()
        return SetLikedResult.Updated
    }

    /**
     * @return タイムラインの投稿の id。保存していない投稿を取ってこられなければ null
     */
    private suspend fun memberOf(postId: String): String? {
        val unsavedObjectUri = UnsavedRemoteNoteLikeId.objectUriOf(postId) ?: return postId
        return remoteNotes.import(unsavedObjectUri)
    }

    /**
     * @return 配信を積んだら true
     */
    private fun setRemoteLiked(accountId: AccountId, senderActorId: String, postId: String, target: LikeTarget.Remote, liked: Boolean): Boolean {
        return if (liked) {
            val like = LikeActivities.like(
                id = "$senderActorId/likes/${UUID.randomUUID()}",
                actor = senderActorId,
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
                val like = LikeActivities.like(id = likeId, actor = senderActorId, objectUri = target.objectUri)
                val undo = LikeActivities.undo(id = "$senderActorId/undo/${UUID.randomUUID()}", like = like)
                AppJson.encodeToString(OutgoingActivity.serializer(), undo)
            }
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

/**
 * 保存していない他のサーバーの投稿を、いいねの postId で指す。タイムラインの id（`r` / `l` / `b` と数字）とは先頭の文字で分ける
 */
object UnsavedRemoteNoteLikeId {
    private const val PREFIX = "u"

    fun of(objectUri: String): String = PREFIX + objectUri

    /**
     * @return [postId] が保存していない投稿を指していなければ null
     */
    fun objectUriOf(postId: String): String? {
        if (!postId.startsWith(PREFIX)) return null
        return postId.removePrefix(PREFIX).takeIf { it.startsWith("https://") }
    }
}

fun interface RemoteNoteImporter {
    /**
     * 他のサーバーの公開の投稿を、保存していなければ取ってきて保存する
     *
     * @return 保存した投稿のタイムラインの id。取れないか、公開か未収載の投稿でなければ null
     */
    suspend fun import(objectUri: String): String?
}
