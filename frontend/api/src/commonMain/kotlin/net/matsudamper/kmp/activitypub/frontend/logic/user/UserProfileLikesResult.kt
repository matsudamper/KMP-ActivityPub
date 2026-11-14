package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserProfileLikesResult {
    /**
     * @param nextCursor 続きが無ければ null
     */
    data class Success(
        val posts: List<UserHomeTimelineResult.Post>,
        val nextCursor: String?,
    ) : UserProfileLikesResult

    /**
     * いいねを公開していない、相手が liked を出していないなど、いいねの一覧が無い
     */
    data object Hidden : UserProfileLikesResult

    data class Failure(
        val message: String,
    ) : UserProfileLikesResult
}
