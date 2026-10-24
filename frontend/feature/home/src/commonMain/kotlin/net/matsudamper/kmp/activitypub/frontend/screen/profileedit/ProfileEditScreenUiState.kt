package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.runtime.Immutable

data class ProfileEditScreenUiState(
    val content: Content,
    val backButtonEnabled: Boolean,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Error(
            val message: String,
        ) : Content

        data class Loaded(
            val displayName: String,
            val displayNameInputEnabled: Boolean,
            val displayNameHasError: Boolean,
            val saveButtonEnabled: Boolean,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onDisplayNameChanged(text: String)

        fun onClickSave()

        fun onClickRetry()

        fun onClickBack()
    }
}
