package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserUpdateProfileIconResult {
    /**
     * @param iconUrl 差し替えた画像の URL。サーバーが返さなければ null
     */
    data class Success(
        val iconUrl: String?,
    ) : UserUpdateProfileIconResult

    data object NotLoggedIn : UserUpdateProfileIconResult

    data object TooLarge : UserUpdateProfileIconResult

    data object DimensionsTooLarge : UserUpdateProfileIconResult

    data object UnsupportedImage : UserUpdateProfileIconResult

    data object UnsupportedOperation : UserUpdateProfileIconResult

    data object InternalServerError : UserUpdateProfileIconResult

    data object FederationDisabled : UserUpdateProfileIconResult

    data class Failure(
        val message: String,
    ) : UserUpdateProfileIconResult
}
