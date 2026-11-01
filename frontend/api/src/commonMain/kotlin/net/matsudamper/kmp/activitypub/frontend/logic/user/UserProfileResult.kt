package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserProfileResult {
    /**
     * @param iconUrl プロフィール画像が無ければ null
     */
    data class Found(
        val username: String,
        val host: String,
        val displayName: String,
        val iconUrl: String?,
        val following: Boolean,
        val biography: String?,
        val posts: List<Post>,
    ) : UserProfileResult

    data object NotFound : UserProfileResult

    data class Post(
        val content: String,
        val publishedAt: String,
    )

    data class Failure(
        val message: String,
    ) : UserProfileResult
}
