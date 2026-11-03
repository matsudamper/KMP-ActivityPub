package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserHomeTimelineResult {
    data class Success(val posts: List<Post>, val nextCursor: String?) : UserHomeTimelineResult
    data object NotLoggedIn : UserHomeTimelineResult

    data class Post(
        val id: String,
        val content: String,
        val publishedAt: String,
        val username: String,
        val host: String?,
        val displayName: String,
        val summary: String?,
        val sensitive: Boolean,
        /**
         * カードにするリンク。OGP は [UserApi.linkPreviews] で別に取る
         */
        val linkUrls: List<String>,
        /**
         * 投稿者のプロフィール画像が無ければ null
         */
        val iconUrl: String?,
        val images: List<UserPostImage>,
    )
}
