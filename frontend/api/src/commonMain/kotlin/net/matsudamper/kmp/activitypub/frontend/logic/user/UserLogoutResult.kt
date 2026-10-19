package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserLogoutResult {
    data object Success : UserLogoutResult

    data class Failure(
        val message: String,
    ) : UserLogoutResult
}
