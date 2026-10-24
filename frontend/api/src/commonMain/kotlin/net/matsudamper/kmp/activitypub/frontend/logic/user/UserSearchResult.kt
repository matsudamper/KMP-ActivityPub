package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserSearchResult {
    data class Success(
        val users: List<User>,
        val nextCursor: String?,
    ) : UserSearchResult

    data class Failure(
        val message: String,
    ) : UserSearchResult

    /**
     * @param host このサーバーのユーザーなら null
     */
    data class User(
        val username: String,
        val host: String?,
        val displayName: String,
    )
}
