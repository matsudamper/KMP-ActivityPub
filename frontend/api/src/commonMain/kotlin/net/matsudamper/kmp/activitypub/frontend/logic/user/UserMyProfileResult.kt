package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserMyProfileResult {
    /**
     * @param displayName 未設定なら null
     * @param iconUrl プロフィール画像が無ければ null
     * @param summary 未設定なら null
     * @param likesPublic いいねした投稿の一覧を公開しているか
     */
    data class Found(
        val username: String,
        val displayName: String?,
        val iconUrl: String?,
        val summary: String?,
        val links: List<UserProfileLink>,
        val likesPublic: Boolean,
        val followingCount: Int,
        val followersCount: Int,
    ) : UserMyProfileResult

    data object NotLoggedIn : UserMyProfileResult

    data class Failure(
        val message: String,
    ) : UserMyProfileResult
}
