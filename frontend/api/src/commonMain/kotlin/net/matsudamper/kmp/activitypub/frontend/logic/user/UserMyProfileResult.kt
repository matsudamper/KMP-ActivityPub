package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserMyProfileResult {
    /**
     * @param displayName 未設定なら null
     */
    data class Found(
        val username: String,
        val displayName: String?,
    ) : UserMyProfileResult

    data object NotLoggedIn : UserMyProfileResult

    data class Failure(
        val message: String,
    ) : UserMyProfileResult
}
