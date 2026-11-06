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
        val posts: List<Post>,
    ) : UserProfileResult

    data object NotFound : UserProfileResult

    /**
     * @param linkPreviewId OGP を [UserApi.linkPreviews] で取るときの id。取れない投稿は null
     * @param postId [UserApi.postDetail] で個別投稿画面を開くときの id。開けない投稿は null
     * @param deletableNoteId ログイン中のアカウントの投稿なら [UserApi.deleteNote] に渡す id。消せない投稿は null
     */
    data class Post(
        val linkPreviewId: String?,
        val postId: String?,
        val deletableNoteId: String?,
        val body: UserPostBody,
    )

    data class Failure(
        val message: String,
    ) : UserProfileResult
}
