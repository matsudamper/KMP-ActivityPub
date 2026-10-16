package net.matsudamper.kmp.activitypub.frontend.logic.passkey

sealed interface PasskeyOptionsResult<out T> {
    data class Success<T>(
        val options: T,
    ) : PasskeyOptionsResult<T>

    data object NotLoggedIn : PasskeyOptionsResult<Nothing>

    data class Failure(
        val message: String,
    ) : PasskeyOptionsResult<Nothing>
}

sealed interface PasskeyLoginResult {
    data object Success : PasskeyLoginResult

    data object NotAccepted : PasskeyLoginResult

    data class Failure(
        val message: String,
    ) : PasskeyLoginResult
}

sealed interface PasskeyRegistrationResult {
    data object Success : PasskeyRegistrationResult

    data object NotLoggedIn : PasskeyRegistrationResult

    data object NotAccepted : PasskeyRegistrationResult

    data object AlreadyRegistered : PasskeyRegistrationResult

    data class Failure(
        val message: String,
    ) : PasskeyRegistrationResult
}
