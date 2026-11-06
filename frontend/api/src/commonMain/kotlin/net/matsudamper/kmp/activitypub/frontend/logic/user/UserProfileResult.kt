package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserProfileResult {
    /**
     * @param iconUrl プロフィール画像が無ければ null
     * @param actorUri フォローできない相手（このサーバーのユーザー）は null
     */
    data class Found(
        val actorUri: String?,
        val username: String,
        val host: String,
        val displayName: String,
        val iconUrl: String?,
        val following: Boolean,
        val biography: String?,
        val links: List<UserProfileLink>,
        val posts: List<Post>,
    ) : UserProfileResult

    data object NotFound : UserProfileResult

    /**
     * @param linkPreviewId OGP を [UserApi.linkPreviews] で取るときの id。取れない投稿は null
     */
    data class Post(
        val linkPreviewId: String?,
        val body: UserPostBody,
    )

    data class Failure(
        val message: String,
    ) : UserProfileResult
}
