package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserUpdateProfileHeaderResult {
    /**
     * @param headerUrl 差し替えた画像の URL。サーバーが返さなければ null
     */
    data class Success(
        val headerUrl: String?,
    ) : UserUpdateProfileHeaderResult

    data object NotLoggedIn : UserUpdateProfileHeaderResult

    data object TooLarge : UserUpdateProfileHeaderResult

    data object DimensionsTooLarge : UserUpdateProfileHeaderResult

    data object UnsupportedImage : UserUpdateProfileHeaderResult

    data object UnsupportedOperation : UserUpdateProfileHeaderResult

    data object InternalServerError : UserUpdateProfileHeaderResult

    data object FederationDisabled : UserUpdateProfileHeaderResult

    data class Failure(
        val message: String,
    ) : UserUpdateProfileHeaderResult
}
