package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserLoginStateResult {
    data object LoggedIn : UserLoginStateResult

    data object NotLoggedIn : UserLoginStateResult

    data class Failure(
        val message: String,
    ) : UserLoginStateResult
}
