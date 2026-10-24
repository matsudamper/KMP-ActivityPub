package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserResolveResult {
    data class Found(
        val user: UserSearchResult.User,
    ) : UserResolveResult

    data object NotFound : UserResolveResult

    data class Failure(
        val message: String,
    ) : UserResolveResult
}
