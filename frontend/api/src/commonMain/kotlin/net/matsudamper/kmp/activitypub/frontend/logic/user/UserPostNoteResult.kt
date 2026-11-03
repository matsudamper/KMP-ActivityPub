package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserPostNoteResult {
    data object Success : UserPostNoteResult

    data object NotLoggedIn : UserPostNoteResult

    data object EmptyBody : UserPostNoteResult

    data object TooLong : UserPostNoteResult

    data object FederationDisabled : UserPostNoteResult

    data object TooManyImages : UserPostNoteResult

    data object ImageTooLarge : UserPostNoteResult

    data object UnsupportedImage : UserPostNoteResult

    data object DescriptionTooLong : UserPostNoteResult

    data object UnsupportedOperation : UserPostNoteResult

    data object InternalServerError : UserPostNoteResult

    data class Failure(
        val message: String,
    ) : UserPostNoteResult
}
