package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostNoteResult {
    data object Success : UserPostNoteResult

    /**
     * @param maxLength 文字数が多すぎる場合の上限
     */
    data class Rejected(
        val isEmpty: Boolean,
        val maxLength: Int?,
    ) : UserPostNoteResult

    data class Failure(
        val message: String,
    ) : UserPostNoteResult
}
