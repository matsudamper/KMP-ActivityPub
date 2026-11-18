package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.TimelineCursor

/**
 * 投稿に付いた返信を引く。このサーバーが保存している投稿だけを見て、相手のサーバーへは取りに行かない
 */
class PostRepliesService(
    private val timeline: TimelineRepository,
    private val noteShareUrls: NoteShareUrls,
    private val blockLists: BlockLists,
) {
    /**
     * @param viewer ログインしていなければ null。ブロックした相手が無いので、誰の返信も隠さない
     * @param postId タイムラインの投稿の id か、[UnsavedRemoteNoteId] の id
     * @return 古い順。[postId] の投稿を読めなければ空
     */
    fun replies(viewer: AccountId?, postId: String): List<TimelinePost> {
        val objectUri = objectUriOf(viewer, postId) ?: return listOf()
        val blockList = viewer?.let(blockLists::of)
        return timeline.replies(viewer = viewer, objectUri = objectUri, limit = REPLY_LIMIT) { actorUri -> blockList?.hidesActor(actorUri) == true }
    }

    private fun objectUriOf(viewer: AccountId?, postId: String): String? {
        val unsavedObjectUri = UnsavedRemoteNoteId.objectUriOf(postId)
        if (unsavedObjectUri != null) return unsavedObjectUri
        if (!postId.matches(TimelineCursor.MEMBER_PATTERN)) return null
        val post = timeline.viewablePost(viewer, postId) ?: return null
        // 返信はこの URL を返信先として記録している
        return noteShareUrls.url(post.noteRef)
    }

    private companion object {
        const val REPLY_LIMIT = 100
    }
}
