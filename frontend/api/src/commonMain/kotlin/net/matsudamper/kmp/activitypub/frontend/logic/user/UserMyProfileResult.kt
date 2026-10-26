package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserMyProfileResult {
    /**
     * @param displayName 未設定なら null
     * @param iconUrl プロフィール画像が無ければ null
     */
    data class Found(
        val username: String,
        val displayName: String?,
        val iconUrl: String?,
    ) : UserMyProfileResult

    data object NotLoggedIn : UserMyProfileResult

    data class Failure(
        val message: String,
    ) : UserMyProfileResult
}
