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
         * @param currentIconUrl 保存済みのプロフィール画像。未設定なら null
         */
        data class Loaded(
            val newIcon: ImageBitmap?,
            val currentIconUrl: String?,
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

        fun onIconReadStart()

        fun onIconPicked(bytes: ByteArray)

        fun onIconTooLarge()

        fun onIconReadError()

        fun onDisplayNameChanged(text: String)

        fun onClickSave()

        fun onClickRetry()

        fun onClickBack()
    }
}
