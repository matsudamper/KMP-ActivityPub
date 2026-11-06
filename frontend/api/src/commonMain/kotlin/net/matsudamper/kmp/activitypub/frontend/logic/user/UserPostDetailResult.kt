package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostDetailResult {
    data class Found(val post: UserHomeTimelineResult.Post) : UserPostDetailResult
    data object NotFound : UserPostDetailResult
    data object NotLoggedIn : UserPostDetailResult
    data class Failure(val message: String) : UserPostDetailResult
}
