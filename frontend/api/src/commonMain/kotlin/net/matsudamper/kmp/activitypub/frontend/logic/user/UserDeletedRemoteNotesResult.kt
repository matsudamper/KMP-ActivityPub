package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserDeletedRemoteNotesResult {
    /**
     * @param notes 受け取った新しい順
     * @param nextCursor 続きが無ければ null
     */
    data class Success(
        val notes: List<UserDeletedRemoteNote>,
        val nextCursor: String?,
    ) : UserDeletedRemoteNotesResult

    data object NotLoggedIn : UserDeletedRemoteNotesResult
}

/**
 * 投稿者が消した他のサーバーの投稿
 *
 * @param actorName 投稿者の preferredUsername。届いていなければ null
 * @param contentText 消される前の本文からタグを外した文字列
 */
data class UserDeletedRemoteNote(
    val id: String,
    val objectUri: String,
    val actorUri: String,
    val actorName: String?,
    val contentText: String,
    val publishedAt: String,
    val deletedAt: String,
)

sealed interface UserPurgeAllDeletedRemoteNotesResult {
    data class Success(val purgedCount: Int) : UserPurgeAllDeletedRemoteNotesResult

    data class Failure(val message: String) : UserPurgeAllDeletedRemoteNotesResult
}
