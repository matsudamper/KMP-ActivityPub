package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * @param deletedPostIds 投稿者が投稿を消して削除済みになった [UserHomeTimelineResult.Post.id]
 * @param boostUndonePostIds ブーストした相手がブーストを取り消した [UserHomeTimelineResult.Post.id]
 */
data class PostRemovals(
    val deletedPostIds: Set<String>,
    val boostUndonePostIds: Set<String>,
) {
    fun isEmpty(): Boolean = deletedPostIds.isEmpty() && boostUndonePostIds.isEmpty()

    operator fun plus(other: PostRemovals): PostRemovals = PostRemovals(
        deletedPostIds = deletedPostIds + other.deletedPostIds,
        boostUndonePostIds = boostUndonePostIds + other.boostUndonePostIds,
    )

    companion object {
        val EMPTY = PostRemovals(deletedPostIds = setOf(), boostUndonePostIds = setOf())
    }
}
