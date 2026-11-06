package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserHomeTimelineResult {
    data class Success(val posts: List<Post>, val nextCursor: String?) : UserHomeTimelineResult
    data object NotLoggedIn : UserHomeTimelineResult

    data class Post(
        val id: String,
        val username: String,
        val host: String?,
        val displayName: String,
        /**
         * 投稿者のプロフィール画像が無ければ null
         */
        val iconUrl: String?,
        val body: UserPostBody,
        /**
         * フォローしている相手のブーストとして載った投稿なら、ブーストした相手。それ以外は null
         */
        val boostedBy: Booster?,
    )

    data class Booster(
        val username: String,
        val host: String,
        val displayName: String,
    )
}
