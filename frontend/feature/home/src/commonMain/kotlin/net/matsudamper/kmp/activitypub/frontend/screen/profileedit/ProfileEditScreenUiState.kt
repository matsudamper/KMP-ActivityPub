package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

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

        /**
         * @param newIcon 選んだがまだ保存していないプロフィール画像。選んでいなければ null
         */
        data class Loaded(
            val newIcon: ImageBitmap?,
            val iconSelectButtonEnabled: Boolean,
            val displayName: String,
            val displayNameInputEnabled: Boolean,
            val displayNameHasError: Boolean,
            val saveButtonEnabled: Boolean,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickSelectIcon()

        fun onIconPicked(bytes: ByteArray)

        fun onDisplayNameChanged(text: String)

        fun onClickSave()

        fun onClickRetry()

        fun onClickBack()
    }
}
