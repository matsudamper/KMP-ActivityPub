package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserProfileResult {
    /**
     * @param iconUrl プロフィール画像が無ければ null
     */
    data class Found(
        val username: String,
        val iconUrl: String?,
    ) : UserProfileResult

    data object NotFound : UserProfileResult

    data class Failure(
        val message: String,
    ) : UserProfileResult
}
