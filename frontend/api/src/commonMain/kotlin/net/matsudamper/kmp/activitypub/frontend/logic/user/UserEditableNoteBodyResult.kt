package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserEditableNoteBodyResult {
    data class Success(val body: String) : UserEditableNoteBodyResult

    data class Failure(val message: String) : UserEditableNoteBodyResult
}
