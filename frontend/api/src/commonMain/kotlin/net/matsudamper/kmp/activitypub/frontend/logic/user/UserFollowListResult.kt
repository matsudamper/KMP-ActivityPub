package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserFollowListResult {
    data class Success(
        val users: List<User>,
        val nextCursor: String?,
    ) : UserFollowListResult

    data object NotLoggedIn : UserFollowListResult

    data class Failure(
        val message: String,
    ) : UserFollowListResult

    /**
     * @param following ログイン中のアカウントがフォローしているか
     * @param username 相手のプロフィールをサーバーが受け取っていなければ null
     * @param displayName 未設定か、相手のプロフィールをサーバーが受け取っていなければ null
     */
    data class User(
        val actorUri: String,
        val following: Boolean,
        val username: String?,
        val host: String,
        val displayName: String?,
        val iconUrl: String?,
    )
}
