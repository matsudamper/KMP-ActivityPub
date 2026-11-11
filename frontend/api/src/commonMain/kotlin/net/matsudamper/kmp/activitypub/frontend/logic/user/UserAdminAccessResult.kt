package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserAdminAccessResult {
    data object Granted : UserAdminAccessResult

    data object Denied : UserAdminAccessResult

    data object NotLoggedIn : UserAdminAccessResult

    data class Failure(
        val message: String,
    ) : UserAdminAccessResult
}
