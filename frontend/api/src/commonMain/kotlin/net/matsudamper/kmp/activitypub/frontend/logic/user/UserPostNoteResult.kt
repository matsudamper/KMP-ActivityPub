package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostNoteResult {
    data object Success : UserPostNoteResult

    data class Rejected(
        val isEmpty: Boolean,
        val maxLength: Int?,
    ) : UserPostNoteResult

    data class Failure(
        val message: String,
    ) : UserPostNoteResult
}
