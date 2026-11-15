package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostRepliesResult {
    /**
     * @param replies 古い順
     */
    data class Success(val replies: List<UserHomeTimelineResult.Post>) : UserPostRepliesResult
    data object NotLoggedIn : UserPostRepliesResult
    data class Failure(val message: String) : UserPostRepliesResult
}
