package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * 投稿に付ける投票
 *
 * @param options 選択肢。並べた順
 * @param expiresInSeconds 投稿してから締め切るまでの秒数
 */
data class UserPostNotePoll(
    val options: List<String>,
    val multipleChoice: Boolean,
    val expiresInSeconds: Int,
)

sealed interface UserPostNoteResult {
    data object Success : UserPostNoteResult

    data object NotLoggedIn : UserPostNoteResult

    data object EmptyBody : UserPostNoteResult

    data object TooLong : UserPostNoteResult

    data object ContentWarningTooLong : UserPostNoteResult

    data object FederationDisabled : UserPostNoteResult

    data object TooManyImages : UserPostNoteResult

    /**
     * 選択肢の数や長さ、締め切りまでの長さが範囲外か、同じ選択肢があるか、画像と一緒に付けた
     */
    data object InvalidPoll : UserPostNoteResult

    /**
     * 置いていないか、期限を過ぎた画像がある
     */
    data object ImageNotFound : UserPostNoteResult

    /**
     * 返信先の投稿が無いか、読めないか、消された
     */
    data object ReplyTargetNotFound : UserPostNoteResult

    /**
     * 引用する投稿が無いか、読めないか、消された
     */
    data object QuoteTargetNotFound : UserPostNoteResult

    /**
     * 宛てるユーザーが無いか、相手のサーバーから引けない
     */
    data object MentionTargetNotFound : UserPostNoteResult

    data class Failure(
        val message: String,
    ) : UserPostNoteResult
}
