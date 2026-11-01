package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserHomeTimelineResult {
    data class Success(val posts: List<Post>, val nextCursor: String?) : UserHomeTimelineResult
    data class Failure(val message: String) : UserHomeTimelineResult
    data object NotLoggedIn : UserHomeTimelineResult

    data class Post(
        val content: String,
        val publishedAt: String,
        val username: String,
        val host: String?,
        val displayName: String,
        val summary: String?,
        val sensitive: Boolean,
    )
}
