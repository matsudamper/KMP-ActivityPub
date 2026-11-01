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
     * @param isLocal このサーバーのユーザーか
     */
    data class User(
        val actorUri: String?,
        val following: Boolean,
        val username: String,
        val host: String,
        val isLocal: Boolean,
        val displayName: String,
        val iconUrl: String?,
    )
}
