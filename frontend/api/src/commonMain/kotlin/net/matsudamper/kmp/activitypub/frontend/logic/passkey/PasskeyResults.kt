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

    data object InvalidName : PasskeyRegistrationResult

    data class Failure(
        val message: String,
    ) : PasskeyRegistrationResult
}

sealed interface PasskeyListResult {
    data class Success(
        val passkeys: List<RegisteredPasskey>,
        val hasPassword: Boolean,
    ) : PasskeyListResult

    data object NotLoggedIn : PasskeyListResult

    data class Failure(
        val message: String,
    ) : PasskeyListResult
}

data class RegisteredPasskey(
    val id: String,
    val name: String,
)

sealed interface PasskeyDeleteResult {
    data object Success : PasskeyDeleteResult

    data object NotLoggedIn : PasskeyDeleteResult

    data object NotFound : PasskeyDeleteResult

    data object LastLoginMethod : PasskeyDeleteResult

    data class Failure(
        val message: String,
    ) : PasskeyDeleteResult
}

sealed interface PasswordDeleteResult {
    data object Success : PasswordDeleteResult

    data object NotLoggedIn : PasswordDeleteResult

    data object NoPasskey : PasswordDeleteResult

    data class Failure(
        val message: String,
    ) : PasswordDeleteResult
}
