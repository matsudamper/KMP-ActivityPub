package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserProfileResult {
    data class Found(
        val username: String,
    ) : UserProfileResult

    data object NotFound : UserProfileResult

    data class Failure(
        val message: String,
    ) : UserProfileResult
}
