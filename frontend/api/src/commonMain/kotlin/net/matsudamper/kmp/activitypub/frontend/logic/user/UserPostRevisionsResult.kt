package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostRevisionsResult {
    /**
     * @param revisions 新しい順。最初が今の版
     * @param nextCursor 続きが無ければ null
     */
    data class Success(val revisions: List<Revision>, val nextCursor: String?) : UserPostRevisionsResult

    data object NotLoggedIn : UserPostRevisionsResult

    /**
     * 開けない投稿
     */
    data object NotFound : UserPostRevisionsResult

    /**
     * @param id 同じ投稿の版の中で版を見分ける
     * @param emojis [content] に入るカスタム絵文字
     * @param revisedAt この版になった時刻。最初の版は投稿した時刻
     */
    data class Revision(
        val id: String,
        val content: String,
        val sensitive: Boolean,
        val mentions: List<UserPostMention>,
        val images: List<UserPostImage>,
        val emojis: List<UserCustomEmoji>,
        val revisedAt: String,
    )
}
