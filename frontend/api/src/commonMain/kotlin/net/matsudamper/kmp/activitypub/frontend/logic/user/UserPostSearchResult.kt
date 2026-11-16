package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostSearchResult {
    /**
     * @param posts 投稿した新しい順
     * @param nextCursor 続きが無ければ null
     */
    data class Success(
        val posts: List<UserHomeTimelineResult.Post>,
        val nextCursor: String?,
    ) : UserPostSearchResult

    data class Failure(
        val message: String,
    ) : UserPostSearchResult
}
