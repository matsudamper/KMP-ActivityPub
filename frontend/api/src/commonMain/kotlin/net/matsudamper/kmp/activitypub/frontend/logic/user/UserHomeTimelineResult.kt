package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserHomeTimelineResult {
    data class Success(val posts: List<Post>, val nextCursor: String?) : UserHomeTimelineResult
    data object NotLoggedIn : UserHomeTimelineResult

    data class Post(
        val id: String,
        val username: String,
        val host: String?,
        val displayName: String,
        val displayNameEmojis: List<UserCustomEmoji>,
        /**
         * 投稿者のプロフィール画像が無ければ null
         */
        val iconUrl: String?,
        val body: UserPostBody,
        /**
         * ログイン中のアカウントの投稿なら [UserApi.deleteNote] に渡す id。消せない投稿は null
         */
        val deletableNoteId: String?,
        /**
         * フォローしている相手のブーストとして載った投稿なら、ブーストした相手。それ以外は null
         */
        val boostedBy: Booster?,
        /**
         * 投稿が載った購読先のグループ。ホームなら null
         */
        val followingGroupId: String?,
    )

    data class Booster(
        val username: String,
        val host: String,
        val displayName: String,
        val displayNameEmojis: List<UserCustomEmoji>,
        /**
         * ブーストした相手のプロフィール画像が無ければ null
         */
        val iconUrl: String?,
    )
}
