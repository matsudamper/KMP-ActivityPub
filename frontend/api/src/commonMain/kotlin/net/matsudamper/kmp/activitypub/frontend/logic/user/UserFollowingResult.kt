package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserFollowingResult {
    data class Success(
        val users: List<User>,
        val nextCursor: String?,
    ) : UserFollowingResult

    data object NotLoggedIn : UserFollowingResult

    data class Failure(
        val message: String,
    ) : UserFollowingResult

    /**
     * @param username 相手のプロフィールをサーバーが受け取っていなければ null
     * @param displayName 未設定か、相手のプロフィールをサーバーが受け取っていなければ null
     */
    data class User(
        val actorUri: String,
        val username: String?,
        val host: String,
        val displayName: String?,
        val iconUrl: String?,
    )
}
