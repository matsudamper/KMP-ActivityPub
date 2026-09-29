package net.matsudamper.kmp.activitypub.frontend.logic.admin
sealed interface AdminSessionResult {
    data class Success(
        val loggedIn: Boolean,
        val passwordConfigured: Boolean,
    ) : AdminSessionResult

    data class Failure(
        val message: String,
    ) : AdminSessionResult
}
