package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.TimelineService

/**
 * タイムラインやプロフィールに出る投稿の id から、個別投稿画面に出す投稿を引く。
 * 保存していない他のサーバーの投稿は [UnsavedRemoteNoteId] の id で指される
 *
 * @param unsavedNotes 他のサーバーとやりとりしないサーバーでは null。保存していない投稿は開けない
 */
class ViewablePostService(
    private val timeline: TimelineService,
    private val accounts: AccountService,
    private val unsavedNotes: UnsavedRemoteNotes?,
) {
    /**
     * 保存していない他のサーバーの投稿は、取ってきて保存してから返す
     *
     * @return 開けない投稿なら null
     */
    suspend fun post(viewer: AccountId, postId: String): ViewablePost? {
        val member = memberOf(viewer = viewer, postId = postId, importsMissing = true) ?: return null
        val post = timeline.viewablePost(viewer, member) ?: return null
        return ViewablePost(post = post, ownedByViewer = isOwnedByViewer(viewer, post))
    }

    /**
     * 他の人の投稿も開けるので、消したり固定したりできるかはこれで分ける
     */
    fun isOwnedByViewer(viewer: AccountId, post: TimelinePost): Boolean =
        post.boostedBy == null && post.localPublicId != null && post.username == accounts.account(viewer)?.username

    /**
     * OGP を引くためだけに投稿を保存しないよう、保存していない投稿は null を返す
     *
     * @return 開けないか、保存していない投稿なら null
     */
    suspend fun contentHtml(viewer: AccountId, postId: String): String? {
        val member = memberOf(viewer = viewer, postId = postId, importsMissing = false) ?: return null
        return timeline.contentHtml(viewer, member)
    }

    private suspend fun memberOf(viewer: AccountId, postId: String, importsMissing: Boolean): String? {
        if (UnsavedRemoteNoteId.objectUriOf(postId) == null) return postId
        return unsavedNotes?.memberOf(viewer = viewer, postId = postId, importsMissing = importsMissing)
    }
}

/**
 * @param ownedByViewer 見ているアカウント自身の投稿か。タイムラインと違い他の人の投稿も開けるので、消したり固定したりできるかはこれで分ける
 */
data class ViewablePost(
    val post: TimelinePost,
    val ownedByViewer: Boolean,
)
