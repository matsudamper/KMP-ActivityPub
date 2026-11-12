package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostNoteResult {
    data object Success : UserPostNoteResult

    data object NotLoggedIn : UserPostNoteResult

    data object EmptyBody : UserPostNoteResult

    data object TooLong : UserPostNoteResult

    data object FederationDisabled : UserPostNoteResult

    data object TooManyImages : UserPostNoteResult

    /**
     * 置いていないか、期限を過ぎた画像がある
     */
    data object ImageNotFound : UserPostNoteResult

    /**
     * 返信先の投稿が無いか、読めないか、消された
     */
    data object ReplyTargetNotFound : UserPostNoteResult

    data class Failure(
        val message: String,
    ) : UserPostNoteResult
}
