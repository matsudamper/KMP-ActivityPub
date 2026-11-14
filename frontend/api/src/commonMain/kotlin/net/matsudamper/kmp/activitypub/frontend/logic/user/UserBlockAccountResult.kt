package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserBlockAccountResult {
    data class Blocked(
        val account: UserBlockedAccount,
    ) : UserBlockAccountResult

    data class Failure(
        val message: String,
    ) : UserBlockAccountResult
}
