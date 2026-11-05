package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserUploadNoteImageResult {
    data class Success(
        val imageId: String,
    ) : UserUploadNoteImageResult

    data object NotLoggedIn : UserUploadNoteImageResult

    data object TooLarge : UserUploadNoteImageResult

    data object UnsupportedImage : UserUploadNoteImageResult

    data object DescriptionTooLong : UserUploadNoteImageResult

    data object UnsupportedOperation : UserUploadNoteImageResult

    data object InternalServerError : UserUploadNoteImageResult

    data class Failure(
        val message: String,
    ) : UserUploadNoteImageResult
}
