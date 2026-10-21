package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserSearchResult {
    data class Success(
        val usernames: List<String>,
        val nextCursor: String?,
    ) : UserSearchResult

    data class Failure(
        val message: String,
    ) : UserSearchResult
}
