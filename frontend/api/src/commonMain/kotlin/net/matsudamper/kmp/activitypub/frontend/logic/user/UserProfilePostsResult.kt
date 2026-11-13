package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserProfilePostsResult {
    /**
     * @param nextCursor 続きが無ければ null
     */
    data class Success(
        val posts: List<UserProfileResult.Post>,
        val nextCursor: String?,
    ) : UserProfilePostsResult

    data class Failure(
        val message: String,
    ) : UserProfilePostsResult
}
