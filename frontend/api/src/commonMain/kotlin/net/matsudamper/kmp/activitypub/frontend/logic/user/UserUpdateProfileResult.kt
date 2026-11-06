package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserUpdateProfileResult {
    data object Success : UserUpdateProfileResult

    data object NotLoggedIn : UserUpdateProfileResult

    data object DisplayNameTooLong : UserUpdateProfileResult

    data object SummaryTooLong : UserUpdateProfileResult

    data object TooManyLinks : UserUpdateProfileResult

    data object LinkTooLong : UserUpdateProfileResult

    /**
     * 見出しが空か、URL が http / https ではない
     */
    data object InvalidLink : UserUpdateProfileResult

    data object FederationDisabled : UserUpdateProfileResult

    data class Failure(
        val message: String,
    ) : UserUpdateProfileResult
}
