package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostResolveResult {
    data class Found(
        val post: UserHomeTimelineResult.Post,
    ) : UserPostResolveResult

    data object NotFound : UserPostResolveResult

    data class Failure(
        val message: String,
    ) : UserPostResolveResult
}
