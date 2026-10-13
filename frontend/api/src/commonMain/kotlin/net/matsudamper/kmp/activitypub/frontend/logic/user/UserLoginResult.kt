package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserLoginResult {
    data object Success : UserLoginResult

    data object WrongUsernameOrPassword : UserLoginResult

    data class Failure(
        val message: String,
    ) : UserLoginResult
}
