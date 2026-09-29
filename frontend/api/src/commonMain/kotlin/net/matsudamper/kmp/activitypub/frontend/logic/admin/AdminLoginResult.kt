package net.matsudamper.kmp.activitypub.frontend.logic.admin
sealed interface AdminLoginResult {
    data object Success : AdminLoginResult

    data object WrongPassword : AdminLoginResult

    data object NotConfigured : AdminLoginResult

    data class Failure(
        val message: String,
    ) : AdminLoginResult
}
