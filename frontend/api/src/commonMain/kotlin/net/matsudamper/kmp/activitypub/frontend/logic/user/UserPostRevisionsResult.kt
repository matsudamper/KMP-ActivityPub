package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostRevisionsResult {
    /**
     * @param revisions 古い順。最後が今の版。開けない投稿なら空
     */
    data class Success(val revisions: List<Revision>) : UserPostRevisionsResult

    data object NotLoggedIn : UserPostRevisionsResult

    data class Failure(val message: String) : UserPostRevisionsResult

    /**
     * @param emojis [content] に入るカスタム絵文字
     * @param revisedAt この版になった時刻。最初の版は投稿した時刻
     */
    data class Revision(
        val content: String,
        val sensitive: Boolean,
        val mentions: List<UserPostMention>,
        val images: List<UserPostImage>,
        val emojis: List<UserCustomEmoji>,
        val revisedAt: String,
    )
}
