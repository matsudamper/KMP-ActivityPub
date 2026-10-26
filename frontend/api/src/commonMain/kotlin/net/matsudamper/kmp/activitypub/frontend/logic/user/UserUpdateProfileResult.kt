package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserUpdateProfileResult {
    data object Success : UserUpdateProfileResult

    data object NotLoggedIn : UserUpdateProfileResult

    data object TooLong : UserUpdateProfileResult

    data object FederationDisabled : UserUpdateProfileResult

    data class Failure(
        val message: String,
    ) : UserUpdateProfileResult
}
